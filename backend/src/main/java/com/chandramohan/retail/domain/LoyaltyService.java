package com.chandramohan.retail.domain;

import static com.chandramohan.retail.domain.Rules.*;

import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoyaltyService {
  public record Rule(
      String name,
      String code,
      String segment,
      String startsAt,
      String endsAt,
      BigDecimal minSpend,
      int discountPercent,
      int pointsPerTen,
      int maxUses) {}

  public record Campaign(String id, int version, boolean active, Rule rule) {}

  public record Wallet(int points, BigDecimal lifetimeSpend, String segment) {}

  public record Preview(
      boolean eligible,
      String reason,
      BigDecimal gross,
      BigDecimal discount,
      BigDecimal net,
      int earned,
      String campaignId,
      int version) {}

  public record Purchase(
      String id,
      BigDecimal gross,
      BigDecimal discount,
      BigDecimal net,
      int earned,
      int redeemed,
      String status,
      String campaignId,
      int version,
      String createdAt) {}

  public record Ledger(
      String id, String purchaseId, String kind, int delta, int balanceAfter, String createdAt) {}

  public record Event(String action, String resourceId, String detail, String createdAt) {}

  private final JdbcTemplate db;

  public LoyaltyService(JdbcTemplate db) {
    this.db = db;
  }

  private void ensure(String user) {
    lock(db, user);
    if (db.queryForObject("SELECT COUNT(*) FROM wallets WHERE owner_id=?", Integer.class, user)
        == 0) db.update("INSERT INTO wallets VALUES(?,0,0)", user);
  }

  @Transactional
  public Wallet wallet(String user) {
    ensure(user);
    return db.query(
            "SELECT * FROM wallets WHERE owner_id=?",
            (r, n) ->
                new Wallet(
                    r.getInt("points"),
                    r.getBigDecimal("spend"),
                    r.getBigDecimal("spend").compareTo(new BigDecimal("500")) >= 0
                        ? "VIP"
                        : r.getBigDecimal("spend").signum() == 0 ? "NEW" : "REGULAR"),
            user)
        .get(0);
  }

  private Campaign campaign(String id, boolean lockRow) {
    var rows =
        db.query(
            "SELECT c.active,v.* FROM campaigns c JOIN campaign_versions v ON c.id=v.id AND"
                + " c.version=v.version WHERE c.id=?"
                + (lockRow ? " FOR UPDATE" : ""),
            (r, n) ->
                new Campaign(
                    r.getString("id"),
                    r.getInt("version"),
                    r.getBoolean("active"),
                    new Rule(
                        r.getString("name"),
                        r.getString("code"),
                        r.getString("segment"),
                        r.getString("starts_at"),
                        r.getString("ends_at"),
                        r.getBigDecimal("min_spend"),
                        r.getInt("discount_percent"),
                        r.getInt("points_per_ten"),
                        r.getInt("max_uses"))),
            id);
    return rows.stream().findFirst().orElseThrow(Rules::missing);
  }

  public List<Campaign> campaigns() {
    return db.queryForList("SELECT id FROM campaigns ORDER BY id", String.class).stream()
        .map(id -> campaign(id, false))
        .toList();
  }

  private void validate(Rule r) {
    check(
        r != null && r.name() != null && r.name().length() > 0 && r.name().length() <= 80,
        "Name must be 1-80 characters");
    check(
        r.code() != null && r.code().matches("[A-Z0-9_-]{3,30}"),
        "Coupon code: 3-30 uppercase letters/digits");
    check(
        r.segment() != null && Set.of("ALL", "NEW", "REGULAR", "VIP").contains(r.segment()),
        "Unknown segment");
    check(
        r.startsAt() != null
            && r.endsAt() != null
            && Instant.parse(r.startsAt()).isBefore(Instant.parse(r.endsAt())),
        "Valid start/end timestamps required");
    check(
        r.minSpend() != null
            && r.minSpend().signum() >= 0
            && r.minSpend().scale() <= 2
            && r.minSpend().compareTo(new BigDecimal("100000")) <= 0,
        "Invalid minimum spend");
    check(
        r.discountPercent() >= 0
            && r.discountPercent() <= 50
            && r.pointsPerTen() >= 0
            && r.pointsPerTen() <= 100
            && r.maxUses() >= 1
            && r.maxUses() <= 100000,
        "Discount 0-50%, points per EUR10 0-100, uses 1-100000");
  }

  private void audit(String user, String action, String id, String detail) {
    db.update("INSERT INTO audit VALUES(?,?,?,?,?,?)", id(), user, action, id, detail, now());
  }

  @Transactional
  public Campaign save(String admin, String id, Rule rule) {
    admin(db, admin);
    db.queryForObject("SELECT id FROM rule_mutex WHERE id=1 FOR UPDATE", Integer.class);
    validate(rule);
    int version = 1;
    if (id == null) {
      id = id();
    } else version = campaign(id, true).version() + 1;
    var sameCode =
        db.queryForList(
            "SELECT c.id FROM campaigns c JOIN campaign_versions v ON c.id=v.id AND"
                + " c.version=v.version WHERE v.code=? AND c.id<>?",
            String.class,
            rule.code(),
            id);
    conflict(sameCode.isEmpty(), "Coupon code already assigned");
    db.update(
        "INSERT INTO campaign_versions VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        id,
        version,
        rule.name(),
        rule.code(),
        rule.segment(),
        Instant.parse(rule.startsAt()).toString(),
        Instant.parse(rule.endsAt()).toString(),
        rule.minSpend(),
        rule.discountPercent(),
        rule.pointsPerTen(),
        rule.maxUses());
    if (version == 1) db.update("INSERT INTO campaigns VALUES(?,?,TRUE)", id, version);
    else db.update("UPDATE campaigns SET version=? WHERE id=?", version, id);
    audit(admin, "CAMPAIGN_SAVED", id, "version " + version);
    return campaign(id, false);
  }

  @Transactional
  public void activate(String admin, String id, boolean active) {
    admin(db, admin);
    campaign(id, true);
    db.update("UPDATE campaigns SET active=? WHERE id=?", active, id);
    audit(admin, "CAMPAIGN_ACTIVE", id, "active=" + active);
  }

  public List<Map<String, Object>> versions(String admin, String id) {
    admin(db, admin);
    return db.queryForList("SELECT * FROM campaign_versions WHERE id=? ORDER BY version DESC", id);
  }

  private void amount(BigDecimal amount) {
    check(
        amount != null
            && amount.compareTo(new BigDecimal("0.01")) >= 0
            && amount.compareTo(new BigDecimal("100000")) <= 0
            && amount.scale() <= 2,
        "Purchase amount: EUR0.01-100000, up to two decimals");
  }

  @Transactional
  public Preview preview(String user, String campaignId, BigDecimal amount) {
    amount(amount);
    var w = wallet(user);
    if (campaignId == null || campaignId.isBlank())
      return new Preview(
          true,
          "Standard earn rule",
          amount,
          BigDecimal.ZERO,
          money(amount),
          amount.divideToIntegralValue(BigDecimal.TEN).intValue(),
          null,
          0);
    var c = campaign(campaignId, false);
    String reason = "Eligible";
    var r = c.rule();
    var time = Instant.now();
    if (!c.active()) reason = "Campaign paused";
    else if (time.isBefore(Instant.parse(r.startsAt()))
        || !time.isBefore(Instant.parse(r.endsAt()))) reason = "Outside campaign schedule";
    else if (!r.segment().equals("ALL") && !r.segment().equals(w.segment()))
      reason = "Customer segment not eligible";
    else if (amount.compareTo(r.minSpend()) < 0) reason = "Minimum spend not met";
    else if (db.queryForObject(
            "SELECT COUNT(*) FROM purchases WHERE campaign_id=? AND status='POSTED'",
            Integer.class,
            campaignId)
        >= r.maxUses()) reason = "Campaign redemption limit reached";
    else if (db.queryForObject(
            "SELECT COUNT(*) FROM purchases WHERE owner_id=? AND campaign_id=? AND status='POSTED'",
            Integer.class,
            user,
            campaignId)
        > 0) reason = "Coupon already used by customer";
    boolean ok = reason.equals("Eligible");
    var discount =
        ok
            ? money(
                amount
                    .multiply(BigDecimal.valueOf(r.discountPercent()))
                    .divide(new BigDecimal("100")))
            : new BigDecimal("0.00");
    var net = money(amount.subtract(discount));
    int earned = ok ? net.divideToIntegralValue(BigDecimal.TEN).intValue() * r.pointsPerTen() : 0;
    return new Preview(ok, reason, amount, discount, net, earned, campaignId, c.version());
  }

  private Purchase purchase(String user, String id, boolean locked) {
    return db
        .query(
            "SELECT * FROM purchases WHERE owner_id=? AND id=?" + (locked ? " FOR UPDATE" : ""),
            (r, n) ->
                new Purchase(
                    r.getString("id"),
                    r.getBigDecimal("gross"),
                    r.getBigDecimal("discount"),
                    r.getBigDecimal("net"),
                    r.getInt("earned"),
                    r.getInt("redeemed"),
                    r.getString("status"),
                    r.getString("campaign_id"),
                    r.getInt("campaign_version"),
                    r.getString("created_at")),
            user,
            id)
        .stream()
        .findFirst()
        .orElseThrow(Rules::missing);
  }

  private void entry(String user, String purchase, String kind, int delta) {
    db.update("UPDATE wallets SET points=points+? WHERE owner_id=?", delta, user);
    int balance =
        db.queryForObject("SELECT points FROM wallets WHERE owner_id=?", Integer.class, user);
    db.update(
        "INSERT INTO points_ledger VALUES(?,?,?,?,?,?,?)",
        id(),
        user,
        purchase,
        kind,
        delta,
        balance,
        now());
  }

  @Transactional
  public Purchase post(String user, String campaign, BigDecimal gross, int redeem, String key) {
    key(key);
    amount(gross);
    check(redeem >= 0 && redeem <= 10000, "Redeem 0-10000 points");
    ensure(user);
    String fingerprint = Objects.toString(campaign, "") + ":" + money(gross) + ":" + redeem;
    var previous =
        db.queryForList(
            "SELECT id,fingerprint FROM purchases WHERE owner_id=? AND request_key=?", user, key);
    if (!previous.isEmpty()) {
      conflict(
          previous.get(0).get("fingerprint").equals(fingerprint),
          "Request key reused for a different purchase");
      return purchase(user, (String) previous.get(0).get("id"), false);
    }
    if (campaign != null && !campaign.isBlank()) campaign(campaign, true);
    var preview = preview(user, campaign, gross);
    conflict(preview.eligible(), preview.reason());
    conflict(wallet(user).points() >= redeem, "Insufficient points");
    var credit = BigDecimal.valueOf(redeem).divide(new BigDecimal("100"));
    conflict(credit.compareTo(preview.net()) <= 0, "Points credit exceeds purchase total");
    BigDecimal net = money(preview.net().subtract(credit));
    int rate =
        campaign == null || campaign.isBlank()
            ? 1
            : campaign(campaign, false).rule().pointsPerTen();
    int earned = net.divideToIntegralValue(BigDecimal.TEN).intValue() * rate;
    String id = id();
    db.update(
        "INSERT INTO purchases VALUES(?,?,?,?,?,?,?,?,?,?,?,'POSTED',?)",
        id,
        user,
        key,
        fingerprint,
        gross,
        money(preview.discount().add(credit)),
        net,
        earned,
        redeem,
        preview.campaignId(),
        preview.version(),
        now());
    if (redeem > 0) entry(user, id, "REDEEM", -redeem);
    if (earned > 0) entry(user, id, "EARN", earned);
    db.update("UPDATE wallets SET spend=spend+? WHERE owner_id=?", net, user);
    audit(user, "PURCHASE_POSTED", id, "simulated purchase");
    return purchase(user, id, false);
  }

  @Transactional
  public Purchase reverse(String user, String id) {
    ensure(user);
    var p = purchase(user, id, true);
    if (p.status().equals("REVERSED")) return p;
    conflict(
        wallet(user).points() >= p.earned(),
        "Earned points have been spent; reversal would create a negative balance");
    if (p.earned() > 0) entry(user, id, "EARN_REVERSAL", -p.earned());
    if (p.redeemed() > 0) entry(user, id, "REDEEM_RETURN", p.redeemed());
    db.update("UPDATE wallets SET spend=spend-? WHERE owner_id=?", p.net(), user);
    db.update("UPDATE purchases SET status='REVERSED' WHERE id=?", id);
    audit(user, "PURCHASE_REVERSED", id, "points adjusted");
    return purchase(user, id, false);
  }

  public List<Purchase> purchases(String user) {
    return db
        .queryForList(
            "SELECT id FROM purchases WHERE owner_id=? ORDER BY created_at DESC",
            String.class,
            user)
        .stream()
        .map(id -> purchase(user, id, false))
        .toList();
  }

  public List<Ledger> ledger(String user) {
    return db.query(
        "SELECT * FROM points_ledger WHERE owner_id=? ORDER BY created_at DESC,id",
        (r, n) ->
            new Ledger(
                r.getString("id"),
                r.getString("purchase_id"),
                r.getString("kind"),
                r.getInt("delta"),
                r.getInt("balance_after"),
                r.getString("created_at")),
        user);
  }

  public List<Event> audit(String user) {
    admin(db, user);
    return db.query(
        "SELECT * FROM audit ORDER BY created_at DESC,id",
        (r, n) ->
            new Event(
                r.getString("action"),
                r.getString("resource_id"),
                r.getString("detail"),
                r.getString("created_at")));
  }

  public List<Map<String, Object>> customers(String admin) {
    admin(db, admin);
    return db.queryForList(
        "SELECT u.id,u.email,w.points,w.spend FROM app_users u LEFT JOIN wallets w ON"
            + " u.id=w.owner_id ORDER BY u.email");
  }
}
