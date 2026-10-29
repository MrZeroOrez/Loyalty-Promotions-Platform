package com.chandramohan.retail.domain;

import java.math.BigDecimal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LoyaltyController {
  final LoyaltyService s;

  public LoyaltyController(LoyaltyService s) {
    this.s = s;
  }

  public record Input(String campaignId, BigDecimal gross, int redeem) {}

  public record Active(boolean active) {}

  @GetMapping("/wallet")
  public Object wallet(@AuthenticationPrincipal Jwt j) {
    return s.wallet(j.getSubject());
  }

  @GetMapping("/campaigns")
  public Object campaigns() {
    return s.campaigns();
  }

  @PostMapping("/preview")
  public Object preview(@AuthenticationPrincipal Jwt j, @RequestBody Input b) {
    return s.preview(j.getSubject(), b.campaignId(), b.gross());
  }

  @PostMapping("/purchases")
  public Object post(
      @AuthenticationPrincipal Jwt j,
      @RequestBody Input b,
      @RequestHeader("Idempotency-Key") String key) {
    return s.post(j.getSubject(), b.campaignId(), b.gross(), b.redeem(), key);
  }

  @PostMapping("/purchases/{id}/reverse")
  public Object reverse(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return s.reverse(j.getSubject(), id);
  }

  @GetMapping("/purchases")
  public Object purchases(@AuthenticationPrincipal Jwt j) {
    return s.purchases(j.getSubject());
  }

  @GetMapping("/ledger")
  public Object ledger(@AuthenticationPrincipal Jwt j) {
    return s.ledger(j.getSubject());
  }

  @PostMapping("/admin/campaigns")
  public Object create(@AuthenticationPrincipal Jwt j, @RequestBody LoyaltyService.Rule b) {
    return s.save(j.getSubject(), null, b);
  }

  @PutMapping("/admin/campaigns/{id}")
  public Object edit(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @RequestBody LoyaltyService.Rule b) {
    return s.save(j.getSubject(), id, b);
  }

  @PutMapping("/admin/campaigns/{id}/active")
  public void active(
      @AuthenticationPrincipal Jwt j, @PathVariable String id, @RequestBody Active b) {
    s.activate(j.getSubject(), id, b.active());
  }

  @GetMapping("/admin/campaigns/{id}/versions")
  public Object versions(@AuthenticationPrincipal Jwt j, @PathVariable String id) {
    return s.versions(j.getSubject(), id);
  }

  @GetMapping("/admin/audit")
  public Object audit(@AuthenticationPrincipal Jwt j) {
    return s.audit(j.getSubject());
  }

  @GetMapping("/admin/customers")
  public Object customers(@AuthenticationPrincipal Jwt j) {
    return s.customers(j.getSubject());
  }
}
