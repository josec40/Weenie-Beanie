---
version: 1
slug: "frontend-src-main-tsx"
primary_target: "frontend/src/main.tsx"
related_targets: ["frontend/src/pages","frontend/src/components"]
---

## Surface
Weenie Beanie customer web app (all routes: sign in, Home, Forecast, Loans, Beanstalk, Expenses) plus Phase 9 notifications, live toasts and the presenter-only demo controls. Mode: Operate. Audience: Alex, a retail customer in a tight month; judges watch the scripted demo. Job: "Am I going to be OK, and what can I do about it?" Build path: code-led.

## Direction contract
THESIS: A Capital One-grade banking app where the answer comes first: one plain sentence says whether Alex is covered and when the dip hits, and the offer that fixes it sits beside it. It refuses the KPI-tile dashboard and the scroll-everything single page.
OWN-WORLD: Deep night-navy shell (#0E2A47 app bar, primary actions) on a cool near-white ground (#F4F6F8) with white work surfaces. One living green (#2E7D4F, leaf #7DB665) is reserved for growth. Pinto-bean tan (#C08A53) appears only on the bean. Red (#B3261E) and amber (#9A5B00) appear only for shortfall, late and safeguard states. Manrope is self-hosted with tabular figures. Lucide line icons. Soft 12px radii, offset soft shadows.
STORY: Alex sees the dip, understands the right-sized offer and its exact terms, consents, and watches the money land with on-chain proof. Each on-time repayment plants the claimed bean and grows the beanstalk toward a higher limit.
FIRST VIEWPORT: Navy top bar (wordmark, Home/Forecast/Loans/Beanstalk/Expenses, bell, account). Left 7 columns: the status sentence at display size, balance as-of, and a 12-month runway bar chart with the dip marked. Right 5 columns: the offer card with terms and consent, or the active loan with its claimed bean; below it a beanstalk preview with tier and leaves to the next tier. On mobile it is a single column with a bottom tab bar.
FORM: Direction pinned by the user (Capital One-adjacent shell + beanstalk growth signature); no roll. Signature move: SVG beanstalk where accept claims a bean (unplanted, held on the loan) and on-time repayment plants it, growing one segment and unfurling a leaf. Tier marks sit at 2 and 5 leaves. Rendered statically under reduced motion. seed:c18d471faa2cc4ed (roll recorded; direction pinned by user beats the roll)
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## Open decisions
- Logo in development: wordmark only.
- Capital One marks: footer line "Built on Capital One services", no Capital One logo.
- Credential display name on future mints is unchanged ("ShBean Repayment Credential").
