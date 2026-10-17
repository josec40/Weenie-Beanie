---
name: Weenie Beanie
description: A calm, bank-grade cash-flow app where the answer comes first and repayment grows a beanstalk.
colors:
  navy-900: "#0e2a47"
  navy-800: "#163a5f"
  navy-700: "#1f4e79"
  navy-50: "#edf3f8"
  ground: "#f4f6f8"
  surface: "#ffffff"
  line: "#dfe5eb"
  line-strong: "#c3ced9"
  ink: "#13212f"
  ink-2: "#44546a"
  ink-3: "#5b6b7e"
  focus: "#3a7bc8"
  green-700: "#2e7d4f"
  green-50: "#e9f4ed"
  leaf: "#7db665"
  stalk-ground: "#f3f8f4"
  bean: "#c08a53"
  bean-dark: "#7f5226"
  bean-wash: "#f8f1ea"
  soil: "#4e3b2c"
  red: "#b3261e"
  red-deep: "#8f1d17"
  red-50: "#fcedec"
  amber: "#8a5200"
  amber-50: "#fff3df"
  presenter-slate: "#22313f"
typography:
  display:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "clamp(1.75rem, 1.15rem + 2vw, 2.65rem)"
    fontWeight: 700
    lineHeight: 1.14
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "clamp(1.6rem, 1.3rem + 1vw, 2rem)"
    fontWeight: 750
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  title-lg:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.35rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.02em"
  title:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.01em"
  figure:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "2rem"
    fontWeight: 750
    lineHeight: 1.2
    letterSpacing: "-0.02em"
    fontFeature: "'tnum' 1"
  body:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
  body-sm:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  label:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "0.8rem"
    fontWeight: 700
    lineHeight: 1.4
  wordmark:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.15rem"
    fontWeight: 800
    lineHeight: 1.2
    letterSpacing: "-0.02em"
rounded:
  xs: "6px"
  sm: "8px"
  control: "10px"
  md: "12px"
  pill: "999px"
spacing:
  "1": "4px"
  "2": "8px"
  "3": "12px"
  "4": "16px"
  "5": "20px"
  "6": "24px"
  "8": "32px"
  "12": "48px"
components:
  button-primary:
    backgroundColor: "{colors.navy-900}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    rounded: "{rounded.control}"
    padding: "10px 18px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.navy-700}"
    textColor: "{colors.surface}"
  button-primary-active:
    backgroundColor: "{colors.navy-800}"
  button-primary-disabled:
    backgroundColor: "#8fa0b2"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.navy-900}"
    rounded: "{rounded.control}"
    padding: "10px 18px"
    height: "44px"
  button-secondary-hover:
    backgroundColor: "{colors.navy-50}"
    textColor: "{colors.navy-900}"
  button-danger:
    backgroundColor: "{colors.red}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    padding: "10px 18px"
    height: "44px"
  button-danger-hover:
    backgroundColor: "{colors.red-deep}"
  button-small:
    rounded: "{rounded.control}"
    padding: "6px 12px"
    height: "36px"
  icon-button:
    backgroundColor: "transparent"
    textColor: "{colors.ink-2}"
    rounded: "{rounded.control}"
    size: "40px"
  icon-button-hover:
    backgroundColor: "{colors.navy-50}"
    textColor: "{colors.navy-900}"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "10px 12px"
    height: "44px"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "24px"
  card-compact:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: "18px"
  notice:
    backgroundColor: "{colors.navy-50}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "18px 20px"
  status-calm:
    backgroundColor: "{colors.navy-50}"
    textColor: "{colors.navy-700}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  status-good:
    backgroundColor: "{colors.green-50}"
    textColor: "{colors.green-700}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  status-warn:
    backgroundColor: "{colors.amber-50}"
    textColor: "{colors.amber}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  status-bad:
    backgroundColor: "{colors.red-50}"
    textColor: "{colors.red}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "3px 10px"
  bean-claim:
    backgroundColor: "{colors.bean-wash}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sm}"
    padding: "12px 14px"
  app-bar:
    backgroundColor: "{colors.navy-900}"
    textColor: "{colors.surface}"
    height: "64px"
  tab-bar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-3}"
    padding: "6px 4px"
  presenter-panel:
    backgroundColor: "{colors.presenter-slate}"
    textColor: "{colors.surface}"
    rounded: "{rounded.md}"
    padding: "18px"
    width: "340px"
---

# Design System: Weenie Beanie

## Overview

**Creative North Star: "The Answer, Then the Beanstalk"**

Weenie Beanie dresses as a serious bank: a deep night-navy app bar, a cool near-white ground, white work surfaces with soft lifted shadows, and Manrope set tight with tabular figures. Inside that sober shell, the first thing on every customer screen is a plain sentence that answers "am I going to be OK?" at display size, with a shortfall amount, when there is one, set in red. Density is calm, not sparse: one answer, one chart, one decision card per viewport, separated by space and shadow rather than rules and boxes.

The one place the system becomes illustrative is the beanstalk. A hand-built SVG plant grows one segment and unfurls one leaf per on-time repayment, carries dashed tier marks at two and five leaves, and holds a tan bean beside the soil while a loan is open. Everything colourful in the product is tied to a meaning: green is growth, tan is the bean, red and amber are trouble. Navy does the rest of the work.

The system refuses the KPI-tile dashboard and the scroll-everything single page. It does not use a Capital One logo; the only reference is the footer line "Built on Capital One services". The product logo is in development, so the brand mark is the wordmark with the bean token.

**Key Characteristics:**
- Navy shell, cool ground, white cards, soft two-level shadow.
- The status sentence is the largest type on the page; a shortfall amount inside it turns red, a covered sentence stays ink.
- Colour is semantic: green only for growth, tan only for the bean, red and amber only for shortfall, late and safeguard states.
- Every figure is tabular.
- One illustrated signature (the beanstalk); everything else is plain banking UI with Lucide line icons.
- Motion is short and decelerating; growth is the only slow, celebratory animation, and it is static under reduced motion.

## Colors

A restrained navy-and-cool-grey bank palette with three meaning-bound accents: growth green, bean tan, and alarm red/amber.

### Primary
- **Night Navy** (navy-900): The app bar, primary buttons, the wordmark on light grounds, checkbox accent, active tab and nav states, the login intro panel. The colour of the bank and of committing to an action.
- **Harbour Navy** (navy-700): Links, primary-button hover, chart bars for healthy months, done steps in the settlement tracker, calm status text, icons inside informational notes.
- **Deep Harbour** (navy-800): Pressed state of primary buttons only.
- **Mist Navy** (navy-50): Hover wash for secondary and icon buttons, calm status chips, the offer's "why" note, forecast summary and notice-link backgrounds, selected table rows.

### Secondary
- **Growth Green** (green-700): The beanstalk stalk and leaf outlines, reached tier marks, numbered markers in "How it grows", the final settlement step, positive figures, good status chips, and tier-up / repaid notification icons.
- **Leaf** (leaf): Leaf fill on the beanstalk only.
- **Sprout Wash** (green-50): Background of good status chips.
- **Stalk Ground** (stalk-ground): The faint green stage behind the full-size beanstalk on the Beanstalk page.

### Tertiary
- **Pinto Tan** (bean): The bean glyph: in the wordmark token, the claimed bean beside the soil, the planting bean, and the bean-claim row.
- **Bean Speck** (bean-dark): The two specks on every bean.
- **Bean Wash** (bean-wash): The bean-claim row that sits inside an active loan card.
- **Soil** (soil): The ground band under the beanstalk.

### Neutral
- **Ink** (ink): Primary text, figures, the zero reference line on charts.
- **Slate Ink** (ink-2): Secondary text, field labels, figcaptions, balance-as-of lines.
- **Quiet Ink** (ink-3): Tertiary text: definition-list terms, table headers, timestamps, inactive tab labels, unreached tier labels. Contrast on ground is ≥4.5:1; do not go lighter for text.
- **Cool Ground** (ground): Page background and the consent row.
- **Surface** (surface): Cards, panels, inputs, popovers, toasts, the mobile tab bar.
- **Hairline** (line): Row dividers inside cards, the footer rule, panel header rules.
- **Strong Hairline** (line-strong): Input and secondary-button borders, table header rule, dashed draft outlines, unreached tier lines.
- **Focus Blue** (focus): The 3px focus outline and the 3px input focus halo (at 35% alpha).
- **Presenter Slate** (presenter-slate): The presenter-only demo toggle and panel. Never used in customer UI.

### Alarm
- **Shortfall Red** (red) with **Deep Red** (red-deep) and **Red Wash** (red-50): Shortfall amounts in the status sentence, shortfall chart bars and table rows, form errors, defaulted status, the danger button.
- **Safeguard Amber** (amber) with **Amber Wash** (amber-50): Repayment paused, settlement problem and tier-down states.

### Named Rules
**The Growth Is Green Rule.** Green appears only where something grew or went right: the beanstalk, reached tiers, repaid loans, positive figures, the completed final step. It is never a generic "success" decoration or a brand accent.

**The Bean Is Tan Rule.** Tan belongs to the bean and nothing else. If a surface is tan, a bean is on it.

**The Alarm Budget Rule.** Red and amber appear only for shortfall, late, safeguard and default states. A calm screen has no red on it.

## Typography

**Display Font:** Manrope Variable (self-hosted via @fontsource-variable/manrope), with system-ui, -apple-system, Segoe UI, sans-serif
**Body Font:** Manrope Variable (same stack)

**Character:** One geometric-humanist sans used across a wide weight range (400 to 800). Headlines tighten their tracking; figures are always tabular so money lines up.

### Hierarchy
- **Display** (700, fluid 1.75–2.65rem, 1.14): The one-sentence status answer on Home and the login intro line. The key phrase inside it steps up to 800. Max width 24ch (18ch on login).
- **Headline** (750, fluid 1.6–2rem, 1.2): Page titles (h1).
- **Title Large** (700, 1.35rem, 1.2): The offer card headline.
- **Title** (700, 1.125rem, 1.2): Card and panel headings (h2). Popover and presenter headings drop to 1rem.
- **Figure** (750, 2rem, tabular): The loan principal on a loan card; record figures use the same weight at 1.5rem.
- **Body** (400, 1rem, 1.5): Running text. Long explanations cap at 72ch with 1.6 line height.
- **Body Small** (400–600, 0.875rem): Field labels, figcaptions, notification text, table cells at 0.95rem.
- **Label** (700, 0.8rem): Status chips, table headers, record terms. Sentence case, never uppercase, no tracking.

### Named Rules
**The Answer Is the Headline Rule.** The display size is reserved for the status sentence that answers the customer's question. Nothing else on the page is larger.

**The Tabular Money Rule.** Every amount, date and count uses tabular figures (`font-variant-numeric: tabular-nums`), including chart axis ticks.

## Layout

A centred 1180px container with 24px side padding; main content has 32px top and 56px bottom padding. Pages are vertical grids with a 24px gap.

- **Home** splits 7:5 (answer, balance and runway chart on the left; offer or active loan, then the beanstalk preview, on the right) with a 32px gutter.
- **Forecast** places the chart full width, then a 3:2 grid of month table and a sticky month breakdown.
- **Beanstalk** splits 5:7 with the plant on the left, sticky below the app bar, and title plus facts on the right.
- **Login** is a 1:1 split: a navy intro panel with the plant rising from its bottom edge, and the form centred in a 420px column.

Spacing steps are 4, 8, 12, 16, 20, 24, 32 and 48px. Cards pad 24px (18px on mobile); in-card rows pad 10–14px vertically.

**Responsive.** At 1023px and below, all two-column pages collapse to one column and sticky asides become static. At 819px and below, the top nav and email hide, a fixed five-item bottom tab bar appears (icon above a 0.7rem label, safe-area aware), main padding drops to 20/16px, popovers become full-width sheets under the bar, and toasts lift above the tab bar.

## Elevation & Depth

Depth comes from soft, navy-tinted shadows at two heights; cards carry no borders. Resting content (panels, chart cards, the beanstalk preview) sits on the low shadow. Decision surfaces (the offer card, the active loan card) and overlays (notification panel, toasts, presenter panel) sit on the lifted shadow. Tinted fills (Mist Navy, Bean Wash, Cool Ground) mark nested regions inside a card instead of a second shadow.

### Shadow Vocabulary
- **Resting** (`box-shadow: 0 1px 2px rgba(14, 42, 71, 0.06), 0 8px 24px -8px rgba(14, 42, 71, 0.12)`): Panels, chart cards, beanstalk cards.
- **Lifted** (`box-shadow: 0 2px 4px rgba(14, 42, 71, 0.08), 0 16px 40px -12px rgba(14, 42, 71, 0.28)`): Offer and loan cards, bell panel, toasts, presenter panel and toggle.
- **App Bar** (`box-shadow: 0 1px 0 rgba(255, 255, 255, 0.06), 0 6px 20px -12px rgba(0, 0, 0, 0.5)`): The sticky navy bar only.
- **Focus Halo** (`box-shadow: 0 0 0 3px rgba(58, 123, 200, 0.35)`): Focused inputs.

### Named Rules
**The Two Heights Rule.** There are two card heights: resting for information, lifted for decisions and overlays. Do not invent a third.

## Shapes

Gently rounded and consistent: 12px on cards, panels, popovers and toasts; 10px on buttons, icon buttons and tab items; 8px on inputs, nested notes and in-card rows; 6px on text and row buttons; full pills on status chips, the notification count and progress bars. Chart bars round only their top corners (4px). Hairlines divide rows inside cards; dashed outlines mark things that are not yet real (unconfirmed expense drafts, unreached tier lines, the ring around a claimed bean). The beanstalk is the only organic form: a gently sinuous stalk, almond leaves with a single vein, a bean with two specks, and a soft soil mound.

## Components

### Buttons
Confident and plain: solid navy, bold label, no gradients.
- **Shape:** gently rounded (10px), 44px minimum height (36px for small), 8px icon gap.
- **Primary:** Night Navy fill, white 700-weight label at 0.95rem, 10px × 18px padding.
- **Hover / Active / Focus:** hover lifts to Harbour Navy, pressed to Deep Harbour, 160ms on the standard ease. Focus shows the 3px Focus Blue outline at 2px offset. Disabled turns muted slate (#8fa0b2) with a not-allowed cursor.
- **Secondary:** white with a Strong Hairline border and navy label; hover washes to Mist Navy and the border darkens to Harbour Navy.
- **Danger:** Shortfall Red fill; hover to Deep Red. Presenter tools only.
- **Ghost on dark:** transparent with a 45% white border, for navy and slate grounds.
- **Icon button:** 40px square, transparent, Slate Ink icon; hover Mist Navy. On dark grounds the icon is white with a 14% white hover.
- **Text links with arrows:** 700-weight navy link; the trailing arrow slides 3px right on hover (200ms).

### Chips
- **Style:** pill, 3px × 10px, Label type. Calm (Mist Navy / Harbour Navy), Good (Sprout Wash / Growth Green), Warn (Amber Wash / Amber), Bad (Red Wash / Red).
- **State:** a chip reports loan state; it is not interactive.

### Cards / Containers
- **Corner Style:** 12px.
- **Background:** Surface on Cool Ground. The Beanstalk page plant stage uses Stalk Ground.
- **Shadow Strategy:** Resting or Lifted per the Two Heights Rule.
- **Border:** none. Inner rows use Hairline dividers.
- **Internal Padding:** 24px (18px under 820px). Card headings sit 12px above content.
- **Offer card:** Title Large headline, a Mist Navy "why" note led by a sparkles icon (AI-written explanation), a two-column terms list with hairline rows, a Cool Ground consent row with a 20px navy checkbox, then Accept (primary, disabled until consent) and No Thanks (secondary).
- **Loan card:** heading and status chip on one line, the Figure-size principal with a muted fee, a sentence of status, then the bean-claim row.

### Inputs / Fields
- **Style:** white, 1px Strong Hairline border, 8px radius, 44px minimum height, 500-weight text; placeholder in a muted slate (#6b7a8c). Labels sit 6px above in 600-weight 0.875rem Slate Ink.
- **Hover:** border darkens slightly (#9fb0c1).
- **Focus:** border goes Harbour Navy with the 3px Focus Halo; no outline.
- **Error:** a Red Wash block with Red 600-weight text below the form, announced as an alert.

### Navigation
- **Desktop:** a sticky 64px Night Navy bar: wordmark (bean token + "Weenie Beanie", 800 weight) on the left, then five text links (600 weight, 0.95rem, white at 78%). Hover brightens to white with a 35% white underline; the active link is white with a 3px white bottom bar flush to the bar's edge. Bell, email and sign-out sit on the right.
- **Mobile:** the bar keeps wordmark, bell and sign-out; navigation moves to a fixed bottom tab bar (white at 97%, hairline top border) with 22px Lucide icons over 0.7rem 700 labels; active is Night Navy, inactive Quiet Ink.
- **Skip link:** slides in from above on focus.

### Tables
Hairline-divided rows, 10px × 12px cells, Label-type headers in Quiet Ink over a Strong Hairline. Figures right-aligned and tabular. Shortfall rows wash Red Wash; the selected row washes Mist Navy and its month button gains a solid underline.

### Notifications and Toasts
The bell carries a white count pill ringed in navy. Its panel is a 380px Lifted popover with a sticky header; unread items get a faint blue wash; each item leads with a 20px Lucide icon coloured by meaning (navy info, green growth, amber safeguard, red default). Toasts stack bottom-right (max three), same card language, and rise 8px into place over 320ms.

### Runway Chart
Recharts bar chart of projected end-of-month balance: Harbour Navy bars, Shortfall Red bars for negative months, 4px top radius, horizontal gridlines only, no axis lines, 12px tabular ticks, a solid Ink zero line. The selected month deepens (navy to Night Navy, red to a deeper red) with a 1.5px Ink outline. The figcaption names the first shortfall month in red text.

### Beanstalk (signature)
An inline SVG (220 × 336 viewBox) with an accessible title and description. The stalk is a 6px Growth Green round-capped stroke that sways gently; each on-time repayment adds a 34px segment and an alternating Leaf-filled almond leaf with a Growth Green outline and vein. Dashed Strong Hairline tier lines at two and five leaves label "Tier N · $limit"; once reached they turn solid Growth Green at 45% opacity. A Soil mound anchors the base. An open loan shows a claimed bean beside the soil inside a dashed tan ring. On repayment, the bean drops into the soil (650ms, accelerating), the stalk draws up (900ms) and the new leaf unfurls from its base (600ms, starting 650ms in). Defaults drop two leaves to the soil as faded olive shapes. The preview variant crops the empty sky so the plant fills a 160px column (110px on mobile). Under reduced motion it renders the final state statically.

### Presenter Panel
A presenter-only control: a 44px slate pill toggle fixed bottom-left (moved into the app bar on mobile) that opens a 340px Presenter Slate panel with white primary buttons, ghost and danger buttons, inline confirmation, and devnet links in pale blue. It is deliberately outside the customer palette.

## Do's and Don'ts

### Do:
- **Do** lead each customer screen with one plain sentence at Display size; only a shortfall amount inside it turns Red (at 800 weight), and a covered sentence stays Ink.
- **Do** use Night Navy for the app bar and every primary action, and Harbour Navy for links.
- **Do** separate cards with the Resting or Lifted shadow on Cool Ground, not with borders.
- **Do** set every figure tabular, including chart ticks and table cells.
- **Do** reference the CSS custom properties in `frontend/src/index.css` for colour; never re-type a hex in a component.
- **Do** keep controls at 44px minimum height (36px small, 40px icon) and show the 3px Focus Blue ring on keyboard focus.
- **Do** use Lucide line icons at 16–22px, inheriting the text colour unless a meaning colour applies.
- **Do** honour reduced motion: the beanstalk renders its final state and all transitions collapse.

### Don't:
- **Don't** build KPI-tile dashboards or scroll-everything single pages; one answer, one chart, one decision.
- **Don't** use green for anything that did not grow or succeed, or tan for anything that is not a bean.
- **Don't** put red or amber on a calm screen.
- **Don't** add uppercase labels, tracked-out eyebrows or kickers above headings; labels are sentence case at 0.8rem bold.
- **Don't** add a third shadow height or put borders on cards.
- **Don't** show a Capital One logo; the footer line "Built on Capital One services" is the only reference.
- **Don't** let Presenter Slate or presenter controls leak into customer UI.
