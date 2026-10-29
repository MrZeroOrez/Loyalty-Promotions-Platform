# API reference

Private JSON APIs use `/api/v1`, `Authorization: Bearer <accessToken>`. Auth: POST `/auth/register` or `/auth/login` with `{"email":"...","password":"..."}`; GET `/auth/me`; POST `/auth/logout`. Only register/login and the root/static assets/health are public. Health is `/actuator/health` outside `/api/v1`.

400: invalid input; 401: missing/expired token; 403: admin permission missing; 404: absent/other-owner private resource; 409: business conflict, duplicate email, insufficient stock/points or idempotency conflict. Public registration always creates CUSTOMER; environment-configured bootstrap creates ADMIN. Admin tokens include CUSTOMER and ADMIN roles.

## Loyalty

- GET `/wallet`, `/campaigns`, `/purchases`, `/ledger`.
- POST `/preview` with `{"campaignId":null,"gross":100,"redeem":0}`. Preview is before points redemption and does not post a purchase.
- POST `/purchases` with the same body plus `Idempotency-Key` (8-80 letters/digits/underscore/hyphen). campaignId null means standard rewards.
- POST `/purchases/{id}/reverse`.
- POST `/admin/campaigns`; PUT `/admin/campaigns/{id}` with a Rule:

```json
{"name":"Welcome offer","code":"WELCOME10","segment":"NEW","startsAt":"2026-11-01T00:00:00Z","endsAt":"2026-12-01T00:00:00Z","minSpend":50,"discountPercent":10,"pointsPerTen":2,"maxUses":100}
```

- PUT `/admin/campaigns/{id}/active` with `{"active":false}`.
- GET `/admin/campaigns/{id}/versions`, `/admin/customers`, `/admin/audit`.

Use a current schedule when testing. An old request key with another purchase payload returns 409. Coupon use eligibility and global use caps are checked during posting under a row lock; preview is advisory and may become stale. Code is the user-facing coupon label; UI selection passes its campaignId.
