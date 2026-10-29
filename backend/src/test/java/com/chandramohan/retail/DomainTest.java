package com.chandramohan.retail;

import static org.junit.jupiter.api.Assertions.*;

import com.chandramohan.retail.auth.*;
import com.chandramohan.retail.domain.*;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {"ADMIN_EMAIL=admin@example.test", "ADMIN_PASSWORD=Admin-test-password-123"})
class DomainTest {
  @Autowired LabStore users;
  @Autowired JdbcTemplate db;

  String user() {
    return users
        .register(UUID.randomUUID() + "@example.test", "test-unused-hash")
        .orElseThrow()
        .id()
        .toString();
  }

  String admin() {
    return users.find("admin@example.test").orElseThrow().id().toString();
  }

  void eq(String a, BigDecimal b) {
    assertEquals(0, new BigDecimal(a).compareTo(b));
  }

  @Autowired LoyaltyService s;

  LoyaltyService.Rule rule(String code, String segment, int max) {
    return new LoyaltyService.Rule(
        "Learning campaign",
        code,
        segment,
        Instant.now().minusSeconds(60).toString(),
        Instant.now().plusSeconds(3600).toString(),
        BigDecimal.TEN,
        10,
        2,
        max);
  }

  @Test
  void campaignPreviewAndPurchaseRespectRuleVersion() {
    String u = user();
    var c = s.save(admin(), null, rule("C" + System.nanoTime(), "ALL", 100));
    var preview = s.preview(u, c.id(), new BigDecimal("100"));
    assertTrue(preview.eligible());
    eq("10", preview.discount());
    var p = s.post(u, c.id(), new BigDecimal("100"), 0, "purchase-01");
    eq("90", p.net());
    assertEquals(18, s.wallet(u).points());
    s.save(admin(), c.id(), rule(c.rule().code(), "VIP", 100));
    assertEquals(1, s.purchases(u).get(0).version());
    assertEquals(2, s.versions(admin(), c.id()).size());
    assertFalse(s.preview(user(), c.id(), new BigDecimal("100")).eligible());
  }

  @Test
  void duplicatePurchaseDoesNotEarnTwice() {
    String u = user();
    var p = s.post(u, null, new BigDecimal("100"), 0, "duplicate-01");
    assertEquals(p.id(), s.post(u, null, new BigDecimal("100"), 0, "duplicate-01").id());
    assertEquals(10, s.wallet(u).points());
    assertEquals(1, s.ledger(u).size());
    assertThrows(Exception.class, () -> s.post(u, null, new BigDecimal("101"), 0, "duplicate-01"));
  }

  @Test
  void redeemAndReversalMaintainLedger() {
    String u = user();
    s.post(u, null, new BigDecimal("1000"), 0, "earn-key-01");
    var p = s.post(u, null, new BigDecimal("20"), 100, "redeem-key1");
    eq("19", p.net());
    assertEquals(1, s.wallet(u).points());
    s.reverse(u, p.id());
    s.reverse(u, p.id());
    assertEquals(100, s.wallet(u).points());
    assertEquals(100, s.ledger(u).stream().mapToInt(LoyaltyService.Ledger::delta).sum());
  }

  @Test
  void unauthorizedAdminAndOverspendFail() {
    String u = user();
    assertThrows(Exception.class, () -> s.save(u, null, rule("FORBIDDEN", "ALL", 1)));
    assertThrows(Exception.class, () -> s.audit(u));
    assertThrows(Exception.class, () -> s.post(u, null, BigDecimal.TEN, 1, "overspend-1"));
    assertTrue(s.purchases(u).isEmpty());
    assertTrue(s.ledger(u).isEmpty());
  }

  @Test
  void schedulesAndPauseAreEnforced() {
    String u = user();
    var c = s.save(admin(), null, rule("D" + System.nanoTime(), "ALL", 5));
    s.activate(admin(), c.id(), false);
    assertFalse(s.preview(u, c.id(), new BigDecimal("100")).eligible());
    var r = c.rule();
    s.save(
        admin(),
        c.id(),
        new LoyaltyService.Rule(
            r.name(),
            r.code(),
            r.segment(),
            Instant.now().plusSeconds(3600).toString(),
            Instant.now().plusSeconds(7200).toString(),
            r.minSpend(),
            10,
            2,
            5));
    s.activate(admin(), c.id(), true);
    assertFalse(s.preview(u, c.id(), new BigDecimal("100")).eligible());
  }

  @Test
  void ownershipAndSegmentBoundaries() {
    String u = user(), v = user();
    assertEquals("NEW", s.wallet(u).segment());
    var p = s.post(u, null, new BigDecimal("500"), 0, "segment-key1");
    assertEquals("VIP", s.wallet(u).segment());
    assertThrows(Exception.class, () -> s.reverse(v, p.id()));
    assertTrue(s.purchases(v).isEmpty());
  }

  @Test
  void concurrentCouponCapIsRespected() throws Exception {
    var c = s.save(admin(), null, rule("E" + System.nanoTime(), "ALL", 1));
    String a = user(), b = user();
    var pool = Executors.newFixedThreadPool(2);
    try {
      var rs =
          pool.invokeAll(
              List.<Callable<Boolean>>of(
                  () -> {
                    try {
                      s.post(a, c.id(), new BigDecimal("100"), 0, "cap-key-a1");
                      return true;
                    } catch (Exception e) {
                      return false;
                    }
                  },
                  () -> {
                    try {
                      s.post(b, c.id(), new BigDecimal("100"), 0, "cap-key-b1");
                      return true;
                    } catch (Exception e) {
                      return false;
                    }
                  }));
      int count = 0;
      for (var r : rs) if (r.get()) count++;
      assertEquals(1, count);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void spentPointsCannotBeReversedIntoNegativeBalance() {
    String u = user();
    var p = s.post(u, null, new BigDecimal("1000"), 0, "earn-big-01");
    s.post(u, null, BigDecimal.TEN, 100, "spend-big1");
    assertThrows(Exception.class, () -> s.reverse(u, p.id()));
    assertTrue(s.wallet(u).points() >= 0);
  }
}
