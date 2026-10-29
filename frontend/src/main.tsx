import React, { useState, useEffect, useRef, FormEvent } from "react";
import { createRoot } from "react-dom/client";
import "./style.css";
type Row = Record<string, any>;
type Session = { accessToken: string; userId: string };
type RequestFn = (
  path: string,
  method?: string,
  body?: unknown,
  key?: string,
) => Promise<any>;
const euro = (n: number | string) =>
  new Intl.NumberFormat("en-NL", { style: "currency", currency: "EUR" }).format(
    Number(n || 0),
  );
const form = (e: FormEvent<HTMLFormElement>) => {
  e.preventDefault();
  return Object.fromEntries(new FormData(e.currentTarget));
};
function normal(v: any): any {
  if (Array.isArray(v)) return v.map(normal);
  if (v && typeof v === "object")
    return Object.fromEntries(
      Object.entries(v).map(([k, x]) => [
        k === k.toUpperCase() ? k.toLowerCase() : k,
        normal(x),
      ]),
    );
  return v;
}
function App() {
  const [session, setSession] = useState<Session | null>(null),
    [register, setRegister] = useState(false),
    [role, setRole] = useState("CUSTOMER"),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const request: RequestFn = async (path, method = "GET", body, key) => {
    const res = await fetch("/api/v1" + path, {
      method,
      headers: {
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
        ...(session ? { Authorization: "Bearer " + session.accessToken } : {}),
        ...(key ? { "Idempotency-Key": key } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!res.ok) {
      let message = "Request failed (" + res.status + ")";
      try {
        const b = await res.json();
        message = b.error || message;
      } catch {}
      if (res.status === 401 && session) setSession(null); setRegister(false);
      throw Error(message);
    }
    return res.status === 204 || res.headers.get("content-length") === "0"
      ? null
      : await res.text().then((t) => (t ? normal(JSON.parse(t)) : null));
  };
  async function run(fn: () => Promise<void>) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Request failed");
    } finally {
      setBusy(false);
    }
  }
  async function login(e: FormEvent<HTMLFormElement>) {
    const b = form(e);
    await run(async () => {
      const res = await request(
        "/auth/" + (register ? "register" : "login"),
        "POST",
        b,
      );
      const me = await fetch("/api/v1/auth/me", {
        headers: { Authorization: "Bearer " + res.accessToken },
      }).then((r) => r.json());
      setRole(me.role);
      setSession(res);
    });
  }
  return (
    <>
      <header>
        <div>
          <span className="mark">◈</span>
          <b>RewardStudio</b>
          <small>JAVA + REACT / TYPESCRIPT</small>
        </div>
        <div className="inline">
          <span className="badge">LOCAL SIMULATION</span>
          {session && (
            <button
              disabled={busy}
              onClick={() =>
                run(async () => {
                  await request("/auth/logout", "POST");
                  setSession(null); setRegister(false);
                })
              }
            >
              Log out
            </button>
          )}
        </div>
      </header>
      <main>
        {!session ? (
          <div className="welcome">
            <section>
              <p className="eyebrow">A PLATFORM FOR CUSTOMER DELIGHT</p>
              <h1>Make every visit worth coming back for.</h1>
              <p className="muted">
                Explore rewards as a customer or design flexible, versioned
                promotions as an administrator.
              </p>
              <div className="benefits">
                <span>✓ Transparent points ledger</span>
                <span>✓ Scheduled, segmented promotions</span>
                <span>✓ Versioned rules and audit history</span>
              </div>
              <p className="hint">
                Persistent SQL data. No real payments. Register a customer
                account or sign in with the administrator configured on the
                server.
              </p>
            </section>
            <form className="card stack" onSubmit={login}>
              <h2>{register ? "Create your account" : "Welcome back"}</h2>
              <label>
                Email
                <input
                  type="email"
                  name="email"
                  autoComplete="username"
                  required
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  name="password"
                  minLength={12}
                  maxLength={72}
                  autoComplete={register ? "new-password" : "current-password"}
                  required
                />
              </label>
              <small>12-72 characters, maximum 72 UTF-8 bytes.</small>
              <button disabled={busy} className="primary">
                {register ? "Register" : "Sign in"}
              </button>
              <button type="button" onClick={() => setRegister(!register)}>
                {register
                  ? "Already registered? Sign in"
                  : "New here? Create an account"}
              </button>
            </form>
          </div>
        ) : (
          <Workspace
            request={request}
            run={run}
            busy={busy}
            notice={setNotice}
            role={role}
          />
        )}
        {error && (
          <div role="alert" className="alert error">
            {error}
          </div>
        )}
        {notice && (
          <div role="status" className="alert success">
            {notice}
          </div>
        )}
      </main>
      <footer>
        Learning application · Simulated transactions · Session expires after 15
        minutes; refresh requires sign-in
      </footer>
    </>
  );
}
type Props = {
  request: RequestFn;
  run: (fn: () => Promise<void>) => Promise<void>;
  busy: boolean;
  notice: (s: string) => void;
  role: string;
};
function Stat({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="card stat">
      <small>{label}</small>
      <strong>{value}</strong>
    </div>
  );
}
function Table({
  heads,
  rows,
}: {
  heads: string[];
  rows: React.ReactNode[][];
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {heads.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {r.map((c, j) => (
                <td key={j}>{c}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {!rows.length && <p className="empty">No records yet.</p>}
    </div>
  );
}
function dateInput(value: string | number) { const d=new Date(value);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16); }
function Workspace({ request, run, busy, notice, role }: Props) {
  const [tab, setTab] = useState("Rewards"),
    [wallet, setWallet] = useState<Row>({
      points: 0,
      lifetimeSpend: 0,
      segment: "NEW",
    }),
    [campaigns, setCampaigns] = useState<Row[]>([]),
    [purchases, setPurchases] = useState<Row[]>([]),
    [ledger, setLedger] = useState<Row[]>([]),
    [events, setEvents] = useState<Row[]>([]),
    [customers, setCustomers] = useState<Row[]>([]),
    [preview, setPreview] = useState<Row | null>(null),
    [editing, setEditing] = useState<Row | null>(null),
    [versions, setVersions] = useState<Row[]>([]);
  const pending = useRef<{ fingerprint: string; key: string } | null>(null);
  async function refresh() {
    const [w, c, p, l] = await Promise.all([
      request("/wallet"),
      request("/campaigns"),
      request("/purchases"),
      request("/ledger"),
    ]);
    setWallet(w);
    setCampaigns(c);
    setPurchases(p);
    setLedger(l);
    if (role === "ADMIN") {
      const [a, cs] = await Promise.all([
        request("/admin/audit"),
        request("/admin/customers"),
      ]);
      setEvents(a);
      setCustomers(cs);
    }
  }
  useEffect(() => {
    run(refresh);
  }, []);
  async function purchase(e: FormEvent<HTMLFormElement>) {
    const b = form(e);
    const body = {
      campaignId: b.campaignId || null,
      gross: Number(b.gross),
      redeem: Number(b.redeem || 0),
    };
    const fingerprint = JSON.stringify(body);
    if (pending.current?.fingerprint !== fingerprint)
      pending.current = { fingerprint, key: crypto.randomUUID() };
    await run(async () => {
      await request("/purchases", "POST", body, pending.current!.key);
      pending.current = null;
      await refresh();
      notice("Simulated purchase posted; points and ledger updated");
    });
  }
  async function save(e: FormEvent<HTMLFormElement>) {
    const b = form(e);
    const rule = {
      ...b,
      minSpend: Number(b.minSpend),
      discountPercent: Number(b.discountPercent),
      pointsPerTen: Number(b.pointsPerTen),
      maxUses: Number(b.maxUses),
      startsAt: new Date(String(b.startsAt)).toISOString(),
      endsAt: new Date(String(b.endsAt)).toISOString(),
    };
    await run(async () => {
      await request(
        "/admin/campaigns" + (editing ? "/" + editing.id : ""),
        editing ? "PUT" : "POST",
        rule,
      );
      setEditing(null);
      await refresh();
      notice("Campaign version saved");
    });
  }
  return (
    <>
      <div className="toolbar">
        <div>
          <p className="eyebrow">REWARDS THAT ADD UP</p>
          <h1>A little loyalty. More possibilities.</h1>
        </div>
        <span className="pill">
          {role === "ADMIN" ? "ADMINISTRATOR" : "CUSTOMER"}
        </span>
      </div>
      <nav>
        {[
          "Rewards",
          "Purchases",
          "Points ledger",
          ...(role === "ADMIN"
            ? ["Campaign studio", "Customers", "Audit"]
            : []),
        ].map((t) => (
          <button
            key={t}
            className={tab === t ? "active" : ""}
            disabled={busy}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
        <button disabled={busy} onClick={() => run(refresh)}>
          Refresh
        </button>
      </nav>
      <div className="stats">
        <Stat label="AVAILABLE POINTS" value={wallet.points} />
        <Stat label="POINTS VALUE" value={euro(wallet.points / 100)} />
        <Stat label="NET SPEND" value={euro(wallet.lifetimeSpend)} />
        <Stat label="CUSTOMER SEGMENT" value={wallet.segment} />
      </div>
      {tab === "Rewards" && (
        <div className="grid">
          <section className="card">
            <h2>Try a simulated purchase</h2>
            <form className="stack" onSubmit={purchase}>
              <label>
                Basket total (EUR)
                <input
                  name="gross"
                  type="number"
                  min=".01"
                  max="100000"
                  step=".01"
                  defaultValue="100"
                  required
                />
              </label>
              <label>
                Promotion / coupon
                <select name="campaignId" aria-label="Promotion / coupon">
                  <option value="">Standard earn rule</option>
                  {campaigns.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.rule.code} · {c.rule.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Points to redeem
                <input
                  name="redeem"
                  type="number"
                  min="0"
                  max="10000"
                  step="1"
                  defaultValue="0"
                  required
                />
              </label>
              <button disabled={busy} className="primary">
                Post simulated purchase
              </button>
              <button
                type="button"
                disabled={busy}
                onClick={(e) => {
                  const f = e.currentTarget.form!;
                  const b = Object.fromEntries(new FormData(f));
                  run(async () =>
                    setPreview(
                      await request("/preview", "POST", {
                        campaignId: b.campaignId || null,
                        gross: Number(b.gross),
                        redeem: Number(b.redeem),
                      }),
                    ),
                  );
                }}
              >
                Preview eligibility
              </button>
            </form>
            <p className="hint">
              100 points = EUR1. Standard earning: 1 point per full EUR10 of net
              spend. Points redemption reduces the earning base. VIP at EUR500
              net spend; NEW before your first purchase.
            </p>
            {preview && (
              <div className="subtle" role="region" aria-label="Rule preview">
                <strong>{preview.reason}</strong>
                <p>
                  Discount: {euro(preview.discount)} · Before points redemption:{" "}
                  {euro(preview.net)} · Earn estimate before redemption:{" "}
                  {preview.earned} points
                </p>
                <small>Rule version {preview.version || "standard"}</small>
              </div>
            )}
          </section>
          <section>
            <h2>Available campaigns</h2>
            <div className="stack">
              {campaigns.map((c) => (
                <article className="card" key={c.id}>
                  <div className="row">
                    <span className="pill">{c.rule.code}</span>
                    <small>
                      {c.active ? "ENABLED" : "PAUSED"} · v{c.version}
                    </small>
                  </div>
                  <h3>{c.rule.name}</h3>
                  <p>
                    {c.rule.discountPercent}% off · {c.rule.pointsPerTen} points
                    / EUR10
                  </p>
                  <small>
                    {c.rule.segment} customers · Minimum {euro(c.rule.minSpend)}
                  </small>
                  <p className="hint">
                    {new Date(c.rule.startsAt).toLocaleString()} →{" "}
                    {new Date(c.rule.endsAt).toLocaleString()}
                    <br />
                    One use per customer; global cap {c.rule.maxUses}.
                    Eligibility is checked by the server.
                  </p>
                </article>
              ))}
              {!campaigns.length && (
                <div className="card empty">
                  No campaigns yet. Standard rewards are available; an
                  administrator can create promotions.
                </div>
              )}
            </div>
          </section>
        </div>
      )}
      {tab === "Purchases" && (
        <section className="card">
          <h2>Purchase history</h2>
          <Table
            heads={[
              "ID",
              "Gross",
              "Discount + credit",
              "Net",
              "Earned",
              "Redeemed",
              "Status",
              "",
            ]}
            rows={purchases.map((p) => [
              p.id.slice(0, 8),
              euro(p.gross),
              euro(p.discount),
              euro(p.net),
              p.earned,
              p.redeemed,
              <span className="pill">{p.status}</span>,
              p.status === "POSTED" ? (
                <button
                  disabled={busy}
                  onClick={() =>
                    run(async () => {
                      await request("/purchases/" + p.id + "/reverse", "POST");
                      await refresh();
                      notice("Purchase reversed; points adjusted");
                    })
                  }
                >
                  Reverse
                </button>
              ) : (
                "—"
              ),
            ])}
          />
          <p className="hint">
            Reversals are idempotent. A reversal is rejected if already-spent
            earned points would make the wallet negative.
          </p>
        </section>
      )}
      {tab === "Points ledger" && (
        <section className="card">
          <h2>Points ledger</h2>
          <Table
            heads={["Time", "Purchase", "Type", "Change", "Balance after"]}
            rows={ledger.map((l) => [
              new Date(l.createdAt).toLocaleString(),
              l.purchaseId.slice(0, 8),
              l.kind,
              <strong className={l.delta >= 0 ? "positive" : "negative"}>
                {l.delta}
              </strong>,
              l.balanceAfter,
            ])}
          />
        </section>
      )}
      {tab === "Campaign studio" && (
        <>
          <div className="grid">
            <section className="card">
              <h2>
                {editing
                  ? "New version of " + editing.rule.name
                  : "Create a campaign"}
              </h2>
              <form
                className="stack"
                onSubmit={save}
                key={editing?.id + ":" + editing?.version}
              >
                <label>
                  Name
                  <input
                    name="name"
                    maxLength={80}
                    defaultValue={editing?.rule.name || ""}
                    required
                  />
                </label>
                <label>
                  Coupon code
                  <input
                    name="code"
                    pattern="[A-Z0-9_-]{3,30}"
                    defaultValue={editing?.rule.code || ""}
                    placeholder="WELCOME10"
                    required
                  />
                </label>
                <label>
                  Customer segment
                  <select
                    name="segment"
                    aria-label="Customer segment"
                    defaultValue={editing?.rule.segment || "ALL"}
                  >
                    {["ALL", "NEW", "REGULAR", "VIP"].map((v) => (
                      <option key={v}>{v}</option>
                    ))}
                  </select>
                </label>
                <div className="inline">
                  <label>
                    Starts at
                    <input
                      name="startsAt"
                      type="datetime-local"
                      defaultValue={
                        editing
                          ? dateInput(editing.rule.startsAt)
                          : dateInput(Date.now() - 60000)
                      }
                      required
                    />
                  </label>
                  <label>
                    Ends at
                    <input
                      name="endsAt"
                      type="datetime-local"
                      defaultValue={
                        editing
                          ? dateInput(editing.rule.endsAt)
                          : dateInput(Date.now() + 7 * 86400000)
                      }
                      required
                    />
                  </label>
                </div>
                <div className="inline">
                  <label>
                    Minimum spend
                    <input
                      name="minSpend"
                      type="number"
                      min="0"
                      max="100000"
                      step=".01"
                      defaultValue={editing?.rule.minSpend || 0}
                      required
                    />
                  </label>
                  <label>
                    Discount percent
                    <input
                      name="discountPercent"
                      type="number"
                      min="0"
                      max="50"
                      defaultValue={editing?.rule.discountPercent || 10}
                      required
                    />
                  </label>
                </div>
                <div className="inline">
                  <label>
                    Points per EUR10
                    <input
                      name="pointsPerTen"
                      type="number"
                      min="0"
                      max="100"
                      defaultValue={editing?.rule.pointsPerTen || 2}
                      required
                    />
                  </label>
                  <label>
                    Maximum uses
                    <input
                      name="maxUses"
                      type="number"
                      min="1"
                      max="100000"
                      defaultValue={editing?.rule.maxUses || 100}
                      required
                    />
                  </label>
                </div>
                <button className="primary" disabled={busy}>
                  Save campaign version
                </button>
                {editing && (
                  <button type="button" onClick={() => setEditing(null)}>
                    Cancel editing
                  </button>
                )}
              </form>
            </section>
            <section className="card">
              <h2>Campaign controls</h2>
              {campaigns.map((c) => (
                <div className="campaign-row" key={c.id}>
                  <h3>
                    {c.rule.name} · v{c.version}
                  </h3>
                  <div className="inline">
                    <button disabled={busy} onClick={() => setEditing(c)}>
                      Edit rules
                    </button>
                    <button
                      disabled={busy}
                      onClick={() =>
                        run(async () => {
                          await request(
                            "/admin/campaigns/" + c.id + "/active",
                            "PUT",
                            { active: !c.active },
                          );
                          await refresh();
                        })
                      }
                    >
                      {c.active ? "Pause" : "Enable"}
                    </button>
                    <button
                      disabled={busy}
                      onClick={() =>
                        run(async () =>
                          setVersions(
                            await request(
                              "/admin/campaigns/" + c.id + "/versions",
                            ),
                          ),
                        )
                      }
                    >
                      Version history
                    </button>
                  </div>
                </div>
              ))}
              {versions.length > 0 && (
                <>
                  <h3>Immutable rule history</h3>
                  <Table
                    heads={["Version", "Name", "Discount", "Points / EUR10"]}
                    rows={versions.map((v) => [
                      v.version,
                      v.name,
                      v.discount_percent + "%",
                      v.points_per_ten,
                    ])}
                  />
                </>
              )}
              <div className="subtle section-gap">
                Use Rewards → Preview eligibility to test a rule against your
                current segment and amount. Editing creates a new version;
                existing purchases retain their original version and calculated
                values.
              </div>
            </section>
          </div>
        </>
      )}
      {tab === "Customers" && (
        <section className="card">
          <h2>Customer segments</h2>
          <Table
            heads={["Email", "Points", "Net spend", "Segment"]}
            rows={customers.map((c) => [
              c.email,
              c.points || 0,
              euro(c.spend),
              Number(c.spend) >= 500
                ? "VIP"
                : Number(c.spend) > 0
                  ? "REGULAR"
                  : "NEW",
            ])}
          />
        </section>
      )}
      {tab === "Audit" && (
        <section className="card">
          <h2>Audit trail</h2>
          <Table
            heads={["Time", "Action", "Resource", "Detail"]}
            rows={events.map((e) => [
              new Date(e.createdAt).toLocaleString(),
              e.action,
              e.resourceId.slice(0, 8),
              e.detail,
            ])}
          />
        </section>
      )}
    </>
  );
}

createRoot(document.getElementById("root")!).render(<App />);
