# Weenie Beanie demo script

A 6–8 minute walkthrough of proactive micro-loans with an on-chain repayment reputation. Everything runs on sandbox rails: Capital One Nessie sandbox for banking, Solana devnet for the escrow and credential. No real money moves.

## Before the demo

1. Start Kafka: `docker compose -f infra/docker-compose.yml up -d`.
2. Start the backend in dev mode: `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev`.
   - The log should show Flyway at v9, `BankBootstrap ... ready`, and `Started ShBeanApplication`.
   - Needed in `.env`: `GEMINI_API_KEY` (otherwise explanations use templates, which is fine), `WALLET_MASTER_KEY`, the demo user, the Nessie key and the DB settings.
3. Start the frontend: `cd frontend && npm run dev`, then open http://localhost:5173.
4. Sign in as the demo user (`DEMO_USER_EMAIL` / `DEMO_USER_PASSWORD`).
5. Open **Presenter Tools** (round button, bottom left; in the top bar on phones) and select **Reset Demo**.
   - It takes about a minute. Wait for "Demo reset complete".
   - The reset wipes Alex's loans, offers, notifications and wallet, re-seeds 20 months of bank history, rewinds the clock, and restores two earlier on-time repayments (Tier 1, $500 limit).
6. Check that Home shows a red headline ("… you're projected to be $… short") and an offer card. If the offer says "Preparing your offer…", wait a few seconds; it arrives live.
7. Optional: keep Solana Explorer (devnet) open in a second tab.

Bank SOL budget: each reset plus loan costs about 0.011 devnet SOL. Check with `solana balance 9rKgpQgeCbDhMr6P7k1XgZnUM75DxM2rB6iXvggH5goQ -u devnet`.

## Script

| # | Show | Say |
|---|---|---|
| 1 | **Home**: the headline sentence and runway chart. | "Banks lend after the damage is done. Weenie Beanie looks ahead: Alex's forecast says that in February they'll be about $390 short, because the annual insurance bill lands before the yearly bonus." |
| 2 | **Forecast**: select the red month; point at the breakdown and the AI summary. | "Twenty months of transactions, recurring patterns and Alex's own subscriptions feed a deterministic forecast. Gemini only writes the plain-language summary; it never decides credit." |
| 3 | **Home → offer card**: terms, safeguard, consent. | "The offer is sized to the shortfall, with a flat fee and no interest. Repayment happens automatically in the first month that can afford it, and is skipped if it would push Alex below a safety buffer. Nothing happens without explicit consent." |
| 4 | Tick consent and select **Accept**. A toast arrives and the loan card shows **Bean claimed**. | "Accepting claims a bean. It isn't planted yet; it waits until Alex repays on time." |
| 5 | **Presenter Tools → Advance One Month** until the shortfall month opens. Show the deposit toast, then **Loans**: the step tracker and **Deposit on Solana Explorer**. | "The money moves twice, in sync: the escrow pays out on Solana devnet and the deposit lands in Alex's bank account. Every step is idempotent and recorded, so a crash never pays twice." |
| 6 | **Advance One Month** until the repayment month closes. Repayment toast, then the beanstalk grows a leaf and the credential is minted. | "On time, the bank cranks the repayment through the on-chain delegation Alex approved at acceptance. The bean is planted and the beanstalk grows. The first on-time repayment mints a non-transferable credential to Alex's wallet." |
| 7 | **Beanstalk** page: tier, record, **View credential**, **Open Verification**. | "Partners can verify this reputation without seeing a name, email or account number. Five on-time repayments reach Tier 2 and a $1,000 limit." |
| 8 | Optional terminal: `curl http://localhost:8090/api/public/credentials/<wallet>` | "That's the public, unauthenticated check, read live from the chain." |

Close with: "Proactive instead of reactive, explainable instead of black-box, protected instead of punitive. And the reputation belongs to the customer."

## What to point out if asked

- **Safeguards:**
  - If the balance would drop below the buffer, the repayment is skipped and the customer is told ("Repayment paused to protect your balance").
  - After a skipped repayment there is a one-month grace period; after that, the loan defaults and two leaves fall.
  - A repayment can be pushed once, but a pushed repayment doesn't count as on time.
- **Live updates:** offers, deposits, repayments and beanstalk growth arrive over GraphQL subscriptions (WebSocket). The bell keeps the history.
- **Seeded history:** the two earlier on-time repayments in the demo are seeded on-chain records with no real loans behind them. Say so if asked.

## Troubleshooting

| Symptom | Fix |
|---|---|
| "Reconnecting…" in the top bar | The backend restarted or the WebSocket dropped. It reconnects on its own, and the screens also refresh every 30 s. |
| Settlement slow or retrying ("failed, will retry" in the log) | The public devnet RPC is rate-limiting this network. Wait about 15 s per retry, or set `SOLANA_RPC_URL` to a dedicated devnet endpoint. |
| Explanations read like templates | Gemini is unavailable or out of credits; the fallback is intentional. Check `GEMINI_API_KEY` and billing. |
| No offer appears | The shortfall exceeds the tier limit, or no month can repay it. Reset the demo, then check the message on Home. |
| Reset says "failed" | Usually Nessie is unreachable. Check `NESSIE_API_KEY` and retry. The reset is safe to run again. |
