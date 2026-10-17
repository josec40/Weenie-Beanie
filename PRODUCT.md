# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

- **Primary:** a retail bank customer (demo persona "Alex Rivera") who is getting through a tight month. They check their cash-flow forecast, see a projected shortfall before it happens, decide whether to accept a micro-loan offered ahead of time, and track their repayment reputation. They are managing their own money, often worried about it, and need to understand what will happen to their balance.
- **Evaluating audience:** hackathon judges and bank stakeholders who watch a scripted demo. The UI is still designed as the customer's real banking app, not as a pitch deck.
- **Secondary:** partner institutions that verify a customer's repayment credential through the public, no-PII verification endpoint (machine-readable; no UI today).

## Product Purpose

Weenie Beanie moves small-dollar lending from reactive to proactive. It forecasts a customer's cash flow from their banking history and their own recurring expenses, spots the month they will go negative, and offers a right-sized micro-loan before the shortfall hits. That loan is auto-repaid in the first month that can afford it. On-time repayments build a portable, non-transferable on-chain credential that unlocks higher limits.

Success means the customer avoids an overdraft, understands exactly why they were offered what they were offered, is never drafted into a dangerously low balance, and ends up with a better credit standing than before.

## Positioning

Proactive, explainable, safeguarded micro-loans with a portable on-chain reputation:

- **Forecast-driven:** offers are sized to a predicted shortfall instead of being requested after the damage is done.
- **Deterministic, rule-based underwriting.** AI (Gemini) only parses input and writes plain-language explanations. It never makes the credit decision.
- **Consent-based auto-repay** with balance safeguards (minimum $25 buffer), a grace period, one reschedule, and early repayment.
- **Soulbound repayment credential on Solana:** partners can verify it without seeing personal data. Tier 0 $200 → Tier 1 $500 (2 on-time repayments) → Tier 2 $1,000 (5 on-time repayments).

The application is built on Capital One services (Nessie API for accounts, balances and transactions). It is pitched as a safer, more streamlined way for Capital One to offer micro-loans.

## Operating Context

- Banking data comes from Capital One's Nessie sandbox: customers, accounts, deposits, withdrawals, purchases and bills.
- Demo flow: onboard → forecast dip → proactive offer with AI explanation → explicit consent → disbursement (Solana escrow + Nessie deposit) → advance the simulated month → auto-repayment → credential minted / tier up.
- A simulated clock ("advance month") drives the demo. Months, not days, are the unit of time the customer reasons in.
- Each settlement shows Solana Explorer links (devnet). Mock USDC mirrors the fiat movements.

## Capabilities and Constraints

- **Stack (existing):** Spring Boot backend (GraphQL + REST, Kafka), Postgres/TimescaleDB on Tiger Cloud, React + Vite + TypeScript + Apollo frontend, Solana devnet programs (Anchor).
- **Capabilities:**
  - 12-month forecast with red/green months.
  - Recurring expenses from natural language, confirmed by the user before saving.
  - A proactive offer with fee, repayment month and AI explanation.
  - Accept (explicit consent), decline, reschedule once, repay early.
  - Settlement status with on-chain links.
  - A credential card with tier, limit and progress.
  - A public credential verification endpoint.
- **Constraints:**
  - Devnet and sandbox only; never imply real money or mainnet.
  - Only one open loan per customer.
  - Offers only when the tier cap covers the full shortfall.
  - The UI must show the exact amount, fee and repayment month before consent.
- **Terminology:** micro-loan, offer, shortfall month, repayment month, flat fee, auto-repayment, safeguard / buffer, grace period, reschedule, credit tier, limit, repayment credential.
- **Open decisions:**
  - The logo is still in development.
  - The on-chain credential minted so far is named "ShBean Repayment Credential" (symbol SHBREP), and minted metadata is immutable. Renaming future credentials to the new brand is undecided.
  - Whether Capital One branding or trademarks may appear in the UI is unconfirmed. Until it is, describe the app as built on Capital One services rather than as a Capital One product.

## Brand Commitments

- **Name:** Weenie Beanie. The repository and existing UI copy still use the working name "ShBean".
- **Logo:** in development; no assets exist yet.

## Evidence on Hand

- The seeded demo persona "Alex Rivera": about 20 months of deterministic Nessie history, an annual insurance shortfall and an annual bonus surplus.
- Live devnet deployments (program IDs, mock USDC mint, transactions) are listed in `audit.md`.
- No real users, testimonials, partner banks, pricing, approval rates or outcomes exist. Future work must not invent them.

## Product Principles

1. **Explain before asking.** Every offer shows why, how much, when it comes back, and what it costs, in plain language, before consent.
2. **Protect the balance first.** Safeguards (buffer, grace, reschedule) outrank collection. Never surprise the customer with a draft.
3. **Deterministic decisions, honest AI.** Rules decide; AI only explains or parses, and its output is labelled and has a fallback.
4. **Reputation the customer owns.** Repayment history becomes a portable credential that is verifiable without exposing personal data.
5. **Sandbox honesty.** Make clear it is a demo on sandbox and devnet rails, without undermining the realism of the customer experience.
