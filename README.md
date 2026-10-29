# Loyalty Promotions Platform

Independent end-to-end learning application: React + TypeScript frontend, Java/Spring Boot secured REST API, persistent SQL database, customer/admin authorization, automated domain/API tests, and browser acceptance scripts. Simulated transactions only. No paid services, real payments or external AI API required.

## Windows quick start

1. Extract the source or clone this repository into a writable folder. Install JDK 21 (Java 17 target) and Node 20.19+ (22 recommended).
2. For the **complete admin and customer experience**, right-click/open PowerShell in this folder and run `./start-admin.ps1`. Enter a new administrator email and a strong password of 12-72 UTF-8 bytes. The script opens separate backend and frontend terminal windows. If PowerShell policy blocks the script, use the manual alternative below rather than changing system security policy.
3. Wait for Spring Boot and Vite to finish starting; first run downloads dependencies.
4. Open **http://127.0.0.1:5181**. Sign in with your admin credentials for administration. Log out and register a different email to test a customer account. New registrations never become administrators.
5. Stop both terminal windows with Ctrl+C. Use one project at a time on a low-RAM laptop. They use different ports, so both can run together if resources allow.

`start.cmd` alone starts customer functionality without creating an admin. Existing database admins can still sign in. No default administrator password is shipped. The bootstrap checks the password if the configured admin email already exists; it never promotes an existing account without matching credentials.

### Manual start

Backend terminal (PowerShell):

```powershell
cd backend
$env:ADMIN_EMAIL = Read-Host "Administrator email"
$secret = Read-Host "Administrator password" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
try { $env:ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
.\mvnw.cmd spring-boot:run
```

Frontend terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Backend health: http://127.0.0.1:8091/actuator/health. No separate Maven, PostgreSQL or Docker installation is needed for the default local path. Linux/macOS: set ADMIN_EMAIL/ADMIN_PASSWORD through your shell's secret handling, then `./start.sh`.

## Implemented workflows

- Customer points wallet, signed points ledger, net-spend-derived customer segments and simulated purchase history.
- Earn/redeem points with atomic wallet/ledger/purchase updates; unique request keys prevent duplicate awards.
- Coupons/promotions with code, name, ALL/NEW/REGULAR/VIP segment, scheduled start/end, minimum spend, percentage discount, points rate and global usage cap.
- One coupon use per customer per campaign among non-reversed purchases; campaign row locking serializes cap checks.
- Administrator campaign creation, editing, pause/enable and immutable rule-version history. Purchases retain the exact version/calculated amounts used when posted.
- Customer eligibility/rule preview with reason, discount and projected earning before points redemption.
- Customer-visible purchase reversal, returning redeemed points and reversing earned points/net spend; repeats do not apply twice.
- Administrator customer/segment overview and persisted audit trail for campaign changes, purchases and reversals.

### Rewards rules

100 points = EUR1 demo credit. Standard earning is one point per full EUR10 net spend. Campaign earning is configurable points per full EUR10. Points redemption reduces net spend before earning. NEW means zero accumulated non-reversed net spend; VIP begins at EUR500, otherwise REGULAR. Segments update automatically after purchase/reversal. The preview explicitly shows values **before** points redemption; posting calculates the final result.

Purchase amount EUR0.01-100000, redemption 0-10000 integer points, maximum 50% campaign discount. Every campaign has a fixed use cap. A reversal is rejected if earned points have already been spent and undoing them would make the wallet negative. Reversed purchases free the coupon usage slot. Rule configuration is a validated schema; it never evaluates arbitrary scripts. Campaigns are visible to customers even if ineligible, with reasons shown by preview. Audit is an application log, not a tamper-proof external ledger.

## Data and security

Default database is `backend/data/lab.mv.db`; users and business records survive restarts. Back up the data folder only after stopping the backend. Do not commit it. To reset, stop the app and delete that folder. JWT signing keys are generated at startup, so restart invalidates prior tokens without deleting data. JWTs expire after fifteen minutes and live only in frontend memory; page refresh requires signing in again. Logout revokes the current token. Passwords use BCrypt. Owner/admin checks are enforced server-side, not just hidden UI tabs.

## Verify locally

```powershell
cd backend
.\mvnw.cmd clean verify
cd ..\frontend
npm ci
npm run build
npx playwright install chromium
# Keep backend and frontend running before this next command.
$env:E2E_ADMIN_EMAIL = "your-configured-admin-email"
# Set E2E_ADMIN_PASSWORD securely to your configured admin password.
npm run test:e2e
```

Browser tests create customer records and can change admin demo configuration/stock. Run them on a disposable development database, not a shared or production database. They do not clear existing records. `E2E_BASE_URL` and `PLAYWRIGHT_EXECUTABLE_PATH` are optional. Screenshots for diagnosis stay in the OS temporary directory.

## Docker / Render / PostgreSQL configuration

Root Dockerfile builds React into the Spring Boot JAR, so one process serves UI and API on the same origin. `render.yaml` declares a Free Docker web service and requests database/admin credentials as environment variables. No cloud resource is created by this source.

For external PostgreSQL use DATABASE_URL=`jdbc:postgresql://HOST/DATABASE?sslmode=require`, DATABASE_USERNAME and DATABASE_PASSWORD. Local H2 file storage must not be used as persistent storage on an ephemeral cloud service. PostgreSQL driver/schema configuration is included, but PostgreSQL and actual cloud deployment are not assumed verified; see VERIFICATION.md. Never commit secrets. The default schema is initialized with `CREATE TABLE IF NOT EXISTS`; production migration/versioning is future work.

CI configuration runs Maven tests and the TypeScript/React build. No deployment runs automatically. Docker and cloud usage depend on your own account choices; this repository does not provision paid services.

## Scope

All features above are part of this local learning build. It is not a production grocery/payment system. Live payments, real grocery APIs, AI-generated meal advice, Kafka integration, email/password recovery, MFA, distributed JWT key management, production rate limits, full accessibility certification, and deployed observability are not included. No claim of millions of users, measured performance improvement or production deployment is made.

See API.md and LEARNING_GUIDE.md for request flows and study exercises.
