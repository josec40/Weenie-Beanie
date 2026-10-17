---
name: Weenie Beanie
description: A Capital One-grade banking shell built around one living bean: the answer comes first, and every on-time repayment grows the beanstalk.
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
  on-navy: "#ffffff"
  on-navy-2: "#c9d6e3"
  focus: "#3a7bc8"
  green-700: "#2e7d4f"
  green-600: "#3d9a5f"
  green-50: "#e9f4ed"
  leaf: "#7db665"
  leaf-light: "#a9d18e"
  stalk-ground: "#f3f8f4"
  bean: "#c08a53"
  bean-dark: "#7f5226"
  bean-wash: "#f7ece0"
  soil: "#4e3b2c"
  red: "#b3261e"
  red-deep: "#8f1d17"
  red-50: "#fcedec"
  red-on-navy: "#ff9b93"
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
  greeting:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1.05rem"
    fontWeight: 600
    lineHeight: 1.3
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
  row:
    fontFamily: "'Manrope Variable', system-ui, -apple-system, 'Segoe UI', sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: 1.35
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
  tile: "12px"
  card: "16px"
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
  "16": "64px"
components:
  button-primary:
    backgroundColor: "{colors.navy-900}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "10px 22px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.navy-700}"
    textColor: "{colors.surface}"
  button-primary-active:
    backgroundColor: "{colors.navy-800}"
  button-primary-disabled:
    backgroundColor: "#8fa0b2"
    textColor: "{colors.surface}"
  button-grow:
    backgroundColor: "{colors.green-700}"
    textColor: "{colors.surface}"
    typography: "{typography.body}"
    rounded: "{rounded.pill}"
    padding: "10px 22px"
    height: "44px"
  button-grow-hover:
    backgroundColor: "{colors.green-600}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.navy-900}"
    rounded: "{rounded.pill}"
    padding: "10px 22px"
    height: "44px"
  button-secondary-hover:
    backgroundColor: "{colors.navy-50}"
    textColor: "{colors.navy-900}"
  button-danger:
    backgroundColor: "{colors.red}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    padding: "10px 22px"
    height: "44px"
  button-danger-hover:
    backgroundColor: "{colors.red-deep}"
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
    rounded: "{rounded.control}"
    padding: "10px 12px"
    height: "44px"
  hero-band:
    backgroundColor: "{colors.navy-900}"
    textColor: "{colors.on-navy}"
    padding: "32px 24px 88px"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "24px"
  card-hover:
    backgroundColor: "{colors.surface}"
  action-list:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
    padding: "0"
  action-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.row}"
    padding: "16px 20px"
    height: "64px"
  action-row-hover:
    backgroundColor: "{colors.navy-50}"
  icon-tile:
    backgroundColor: "{colors.navy-50}"
    textColor: "{colors.navy-700}"
    rounded: "{rounded.tile}"
    size: "40px"
  icon-tile-grow:
    backgroundColor: "{colors.green-50}"
    textColor: "{colors.green-700}"
  icon-tile-bean:
    backgroundColor: "{colors.bean-wash}"
    textColor: "{colors.bean-dark}"
  progress-track:
    backgroundColor: "{colors.line}"
    rounded: "{rounded.pill}"
    height: "10px"
  progress-fill:
    backgroundColor: "{colors.green-700}"
    rounded: "{rounded.pill}"
  progress-bubble:
    backgroundColor: "{colors.green-700}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.xs}"
    padding: "2px 8px"
  bean-tile:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.tile}"
    size: "34px"
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
    rounded: "{rounded.tile}"
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
    rounded: "{rounded.card}"
    padding: "18px"
    width: "340px"
---

# Design System: Weenie Beanie

## Overview

**Creative North Star: "A Bank That Grows a Bean"**

Weenie Beanie stands on Capital One's foundations: a deep night-navy shell, a cool near-white ground, white work surfaces, bold plain controls and nothing that looks like a toy. Into that shell it brings the warmth of habit-building money apps such as Acorns, where the whole interface revolves around one small growing thing. For Acorns that is the acorn; here it is **the bean and its beanstalk**. The bean is the brand mark, the unit of progress and the reward. Every screen is built so the customer can see where their bean is: claimed, planted, or grown into leaves.

From that pairing the system takes these components:

- **The hero band** opens Home: a navy band that greets the customer by time of day and states the answer in one sentence. Leaves grow into the band from the right edge.
- **Overlapping card stack:** the first cards rise up out of the hero band.
- **Action lists:** cards made of rows, each with an icon tile, a bold verb and quieter words, an amount and a chevron.
- **Progress track:** a pill bar with a value bubble riding the fill.
- **Pill buttons**, including the green Grow action.
- **Illustration strips:** leaf and bean art on bean-related cards.
- **The bean tile:** the app icon.

Everything a customer can act on responds when touched or hovered. Cards lift, rows wash and their chevrons step forward, leaves sway, the bean wobbles, and bars brighten. Motion is quick and decelerating, so the app feels alive without feeling busy.

The system refuses the KPI-tile dashboard, the scroll-everything single page, neon or crypto styling, confetti for borrowing, and jokey copy. It shows no Capital One logo; the footer line "Built on Capital One services" is the only reference. The product logo is still in development, so the mark is the bean tile with the "Weenie Beanie" wordmark.

**Key Characteristics:**
- Navy app bar, then a navy hero band on Home whose lower edge the first cards overlap.
- One plain status sentence at display size inside the hero. A shortfall amount turns soft coral on navy (Red on Navy); a covered sentence stays white.
- Action-list cards with icon tiles, bold verbs and chevrons. Every row is a link or button and responds on hover.
- Green means growth (the beanstalk, progress fills, reached tiers, the Grow button). Tan is only the bean. Red and amber are only for trouble.
- Pill buttons and 16px cards; Lucide line icons; tabular figures.
- One illustrated world (leaves, bean, soil, stalk) shared by the hero, the beanstalk, empty states and bean-related cards. It never appears behind data.

## Colors

A Capital One-style navy and cool-grey palette, plus three accents that each carry a meaning: growth green, bean tan, and red/amber for trouble.

### Primary
- **Night Navy** (navy-900): The app bar, the Home hero band, primary buttons, the wordmark on light grounds, active tab and nav states, the login intro panel.
- **Harbour Navy** (navy-700): Links, primary-button hover, healthy-month chart bars, calm status text, icons inside navy icon tiles.
- **Deep Harbour** (navy-800): Pressed primary buttons; hover wash on rows inside navy surfaces.
- **Mist Navy** (navy-50): Hover wash for action rows, secondary and icon buttons, navy icon tiles, calm chips, the offer's "why" note, selected table rows.
- **On Navy** (on-navy) and **Navy Secondary Text** (on-navy-2): Text on the app bar and hero band. The secondary text is at least 4.5:1 on Night Navy.

### Secondary (growth)
- **Growth Green** (green-700): The stalk and leaf outlines, progress fills and value bubbles, reached tier marks, the Grow button, positive figures, good chips, and growth notification icons.
- **Sprout** (green-600): Grow button hover, and the leading edge of progress fills.
- **Leaf** (leaf) and **Young Leaf** (leaf-light): Leaf fills on the beanstalk and in the hero and empty-state illustrations.
- **Sprout Wash** (green-50): Growth icon tiles and good chips.
- **Stalk Ground** (stalk-ground): The stage behind the full-size beanstalk.

### Tertiary (the bean)
- **Pinto Tan** (bean) and **Bean Speck** (bean-dark): Every bean: the bean tile, the claimed bean, the planting bean, bean icon tiles.
- **Bean Wash** (bean-wash): The bean-claim row and bean icon tiles.
- **Soil** (soil): The ground under the beanstalk.

### Neutral
- **Ink** (ink), **Slate Ink** (ink-2), **Quiet Ink** (ink-3): Text in three steps. Quiet Ink is the floor, at 4.5:1 or better on Cool Ground.
- **Cool Ground** (ground): Page background.
- **Surface** (surface): Cards, panels, inputs, popovers, toasts, the tab bar, and the bean tile.
- **Hairline** (line) and **Strong Hairline** (line-strong): Row dividers, progress tracks, input borders, dashed "not yet real" outlines.
- **Focus Blue** (focus): The 3px focus ring and the input focus halo.
- **Presenter Slate** (presenter-slate): Presenter tools only. Never in customer UI.

### Alarm
- **Shortfall Red** (red), **Deep Red** (red-deep), **Red Wash** (red-50): Shortfall bars and table rows, form errors, defaulted status, the danger button.
- **Red on Navy** (red-on-navy): The shortfall amount inside the navy hero sentence (contrast at least 4.5:1 on Night Navy).
- **Safeguard Amber** (amber) and **Amber Wash** (amber-50): Repayment paused, settlement problems, tier down.

### Named Rules
**The Growth Is Green Rule.** Green appears only where something grew, will grow, or went right: the beanstalk, progress toward the next tier, reached tiers, repaid loans, the Grow action. It is never a generic brand accent.

**The Bean Is Tan Rule.** Tan belongs to the bean and nothing else. If a surface is tan, a bean is on it.

**The Alarm Budget Rule.** Red and amber appear only for shortfall, late, safeguard and default states. A calm screen has no red on it.

## Typography

**Font:** Manrope Variable (self-hosted via @fontsource-variable/manrope), with system-ui, -apple-system, Segoe UI and sans-serif as fallbacks. A single family serves both display and body.

**Character:** One friendly geometric-humanist sans across weights 400 to 800, close in spirit to Capital One's own rounded sans and to Acorns' soft labels. Headlines tighten their tracking; figures are always tabular.

### Hierarchy
- **Greeting** (600, 1.05rem): "Good morning, Alex", the line above the hero sentence, in Navy Secondary Text.
- **Display** (700, fluid 1.75–2.65rem, 1.14): The one-sentence answer in the hero band and the login intro line. Its key phrase steps up to 800. Max 26ch.
- **Headline** (750, fluid 1.6–2rem): Page titles.
- **Title Large** (700, 1.35rem): Offer headline.
- **Title** (700, 1.125rem): Card headings.
- **Row** (500, 1rem): Action-row text. The leading verb is 700 Ink and the rest is 500 Slate Ink, the Acorns "**Grow** your beanstalk" pattern. Row amounts are 700 and tabular.
- **Figure** (750, 2rem, tabular): Loan principal and record numbers.
- **Body** (400, 1rem, 1.5) and **Body Small** (0.875rem).
- **Label** (700, 0.8rem): Chips, table headers, progress bubbles. Sentence case, never uppercase.

### Named Rules
**The Answer Is the Headline Rule.** Display size is reserved for the hero sentence. Nothing else on the page is larger.

**The Bold Verb Rule.** Action rows start with a bold verb that names what happens ("**Cover** February", "**Grow** your beanstalk", "**Plan** your expenses"); the rest of the phrase is quieter.

**The Tabular Money Rule.** Every amount, date and count is tabular.

## Layout

A centred 1180px container with 24px side padding.

- **Home** opens with the full-width **hero band** directly under the app bar: greeting, status sentence, balance as of, and the leaf illustration on the right. The content grid (7:5) starts inside the band. The left column holds the runway chart card; the right column holds the offer or active-loan card, then the **action list** and the **beanstalk progress card**. The first row of cards overlaps the band by 64px, the Acorns card-stack move.
- **Forecast** keeps the full-width chart, then the 3:2 table and sticky breakdown.
- **Beanstalk** splits 5:7: the plant stage on the left, and on the right the title, progress track and fact cards.
- **Login** is a 1:1 split: a navy panel with the plant rising from its bottom edge, and the form.

Spacing steps: 4, 8, 12, 16, 20, 24, 32, 48, 64px. Cards pad 24px (18px on mobile); action rows pad 16px × 20px.

**Responsive.** At 1023px and below, two-column pages become one column. The hero keeps its overlap, shrinking it to 48px. At 819px and below, the bottom tab bar replaces the top nav and the hero padding drops to 24px × 16px, with a 40px overlap. Popovers become sheets.

## Elevation & Depth

Soft, navy-tinted shadows at two heights; cards have no borders.

### Shadow Vocabulary
- **Resting** (`0 1px 2px rgba(14,42,71,0.06), 0 8px 24px -8px rgba(14,42,71,0.12)`): Information cards and action lists.
- **Lifted** (`0 2px 4px rgba(14,42,71,0.08), 0 16px 40px -12px rgba(14,42,71,0.28)`): Decision cards (offer, active loan), overlays, and the **hover state** of any interactive card.
- **App Bar** (`0 1px 0 rgba(255,255,255,0.06), 0 6px 20px -12px rgba(0,0,0,0.5)`).
- **Focus Halo** (`0 0 0 3px rgba(58,123,200,0.35)`).

### Named Rules
**The Two Heights Rule.** There are two heights only. Hover borrows Lifted; it never invents a third.

**The Honest Lift Rule.** Only surfaces that do something on click lift on hover. Information cards stay still, so lifting always means "this is clickable".

## Shapes

Friendlier than a legacy bank, never bubbly:

- **Corner radii:**
  - 16px: cards, action lists, popovers, toasts.
  - 12px: icon tiles, the bean tile, nested notes.
  - 10px: inputs and icon buttons.
  - Pills: all text buttons, status chips, progress tracks, the notification count.
- **Chart bars:** only the top corners are rounded (4px).
- **Hairlines and dashes:** hairlines divide action rows, which run edge to edge inside their card. Dashed outlines mark things that are not yet real.
- **Organic forms:** the sinuous stalk, almond leaves with one vein, the two-speck bean and the soil mound are the only organic shapes. They are shared by the beanstalk, the hero leaves, empty states and the bean tile.

## Components

### Hero Band (Home)
- **Background and size:** a Night Navy band, full width under the app bar, 32px top padding, 88px bottom padding.
- **Text:**
  - The Greeting line ("Good morning, Alex").
  - The Display status sentence in white; a shortfall amount is in Red on Navy at 800.
  - The balance as of, in Navy Secondary Text.
- **Leaves:** on the right, a cluster of three to five almond leaves (Leaf, Young Leaf and Growth Green, rotated, one partly behind the content) plus one bean. They grow up from the band's bottom edge, decoration only (`aria-hidden`). On pointer hover over the hero, the leaves sway ±3° on staggered delays.
- **Cards:** the cards below overlap the band (see Layout).

### Action List (Acorns-style rows)
- **Container:** a 16px Surface card at Resting height with no inner padding. Rows are full-width links or buttons divided by hairlines.
- **Row:** a 40px **icon tile**, then the Bold Verb text, then an optional tabular amount on the right, then a 20px chevron in Quiet Ink. The tile's colour carries the meaning: navy for information, green for growth, tan for the bean, amber for safeguard. Minimum height is 64px.
- **Hover:** the row washes Mist Navy and the chevron slides 4px right and turns Night Navy, both over 160ms. The icon tile scales to 1.06.
- **Press:** the row background deepens one step.
- **Focus:** a 3px inset Focus Blue ring.

### Progress Track (tier progress)
- **Track and fill:** a 10px pill track in Hairline. A Growth Green fill grows from the left; its leading 24px fades to Sprout.
- **Value bubble:** a Growth Green Label-type bubble with a 4px pointer rides above the fill's end ("2 leaves").
- **Labels:** "Tier N · $limit" at the left end and "3 until Tier 2" at the right end, in Body Small.
- **Animation:** when the value changes, the fill animates width over 600ms on the ease-out curve. This is static under reduced motion.

### Buttons
- **Shape:** pill. 44px minimum height (36px small); 10px × 22px padding; 700-weight label; 8px icon gap.
- **Primary:** Night Navy fill, white label. Hover goes to Harbour Navy and lifts 1px with the Resting shadow; press goes to Deep Harbour at scale 0.98. Disabled is muted slate with no lift.
- **Grow:** a Growth Green pill used only for actions that plant or grow the bean, such as accepting an offer. Hover goes to Sprout.
- **Secondary:** white with a Strong Hairline border and navy label; hover washes Mist Navy.
- **Danger:** Shortfall Red pill, presenter tools only.
- **Icon buttons:** 40px, 10px radius; hover washes Mist Navy; press scales to 0.94.
- **Text links with arrows:** the arrow slides 3px on hover.

### Cards / Containers
- **Shape and shadow:** 16px corners, Surface fill, no border. Resting for information; Lifted for decisions.
- **Interactive cards** (the beanstalk preview, loan rows that link out): hover lifts by translateY(−2px) to the Lifted shadow over 200ms. Press settles back to 0.
- **Offer card:** Title Large headline, Mist Navy "why" note with a sparkles icon, the terms list, the consent row, then **Accept as a Grow pill** and **No Thanks as a Secondary pill**. Along the card's top edge runs a 64px-tall illustration strip on Stalk Ground: soil with a waiting bean.
- **Active loan card:** heading and status chip, the principal at Figure size, a status sentence, the bean-claim row (bean tile plus copy), and a link row to details.

### Bean Tile (brand mark)
A Surface rounded square (12px corner, 34px in the app bar) holding the bean glyph, after the Acorns app icon. On the navy bar it sits as a white tile with the "Weenie Beanie" wordmark beside it. On hover the bean wobbles once (rotate −8°, then 6°, then 0, over 500ms).

### Inputs / Fields
White fill, Strong Hairline border, 10px corners, 44px minimum height. Labels sit above in 600-weight Slate Ink. Hover darkens the border; focus shows a Harbour Navy border plus the Focus Halo. Errors appear in a Red Wash block announced as an alert.

### Navigation
- **Desktop app bar:** a sticky 64px Night Navy bar: the bean tile and wordmark, five text links, then the bell, email and sign-out. Link hover brightens and grows a 2px white underline from the centre. The active link has a 3px white bottom bar.
- **Mobile:** a white bottom tab bar with Lucide icons. The active tab is Night Navy and its icon sits on a Mist Navy pill. Pressing a tab scales it to 0.94.

### Tables
Hairline rows with tabular, right-aligned figures. Hovering a row washes Ground; shortfall rows use Red Wash; the selected row uses Mist Navy with an underlined month.

### Runway Chart
Navy bars for healthy months and red bars for shortfall months, with a 4px top radius, gridlines only and an Ink zero line. **Hovering a bar** brightens it, shows the tooltip card (16px corners, Lifted shadow, month and amount) and a faint navy column cursor. The selected or first-shortfall month is deeper with an Ink outline.

### Notifications and Toasts
- **Bell:** a white count pill ringed in navy. Hovering the bell swings it ±10° once.
- **Panel:** a 380px Lifted popover. Rows are action rows: an icon tile coloured by meaning, a bold title, and quiet body text.
- **Toasts:** they stack bottom-right and rise 8px into place.

### Beanstalk (signature)
- **Drawing:** an inline SVG with an accessible title and description.
  - The stalk is a 6px Growth Green stroke.
  - Each on-time repayment adds one segment and one alternating almond leaf.
  - Tier lines sit at 2 and 5 leaves.
  - The soil mound runs full-bleed.
  - An open loan shows the claimed bean in a dashed tan ring.
- **Growth animation:** the bean drops into the soil (650ms), the stalk draws up (900ms), and the new leaf unfurls (600ms).
- **Hover** (pointer devices only): the leaves sway ±4° in a staggered wave from the base up, and the claimed bean wobbles. All of it settles when the pointer leaves.
- **Reduced motion:** the final state renders statically, with no hover motion.

### Presenter Panel
A presenter-only slate pill toggle and 340px panel. It stays deliberately outside the customer palette.

## Interaction & Motion

**The Responsive Surface Rule.** Everything that can be acted on answers the pointer within 120–200ms, on the ease-out curve `cubic-bezier(0.16, 1, 0.3, 1)`. Hover feedback stays subtle:
- **Movement:** at most 2px of lift, 4px of slide, or 6% of scale.
- **Colour:** at most one step of colour change.

Only `transform`, `opacity`, `background-color`, `box-shadow` and `color` animate; there is never a `transition: all`. Press states give tactile confirmation by scaling to 0.98 on buttons and rows. Hover-only motion is gated behind `@media (hover: hover) and (pointer: fine)`, so touch devices get press states instead. Under `prefers-reduced-motion`, every transform transition is removed and colour changes stay.

## Do's and Don'ts

### Do:
- **Do** open Home with the navy hero band: greeting, one answer sentence, leaves on the right, and cards overlapping its lower edge.
- **Do** build navigation-style lists as action rows: icon tile, bold verb, amount, chevron.
- **Do** show progress toward the next tier as a pill track with a value bubble.
- **Do** use the green Grow pill only for actions that plant or grow the bean.
- **Do** make every clickable surface respond on hover (lift, wash, nudge, sway) and on press (scale 0.98), within 200ms.
- **Do** keep the leaf-and-bean illustration to the hero, beanstalk, bean-related cards, empty states and the brand tile.
- **Do** set every figure tabular and keep controls at least 44px (40px for icon buttons).
- **Do** honour reduced motion and touch: no hover-only information, and no motion without a static equivalent.

### Don't:
- **Don't** build KPI-tile dashboards or scroll-everything pages.
- **Don't** lift information cards that do nothing on click.
- **Don't** use green for anything that didn't grow, or tan for anything that isn't a bean.
- **Don't** put red or amber on a calm screen.
- **Don't** put illustration behind numbers, tables or charts.
- **Don't** add uppercase eyebrows or kickers, gradient text, glass, or neon.
- **Don't** show a Capital One logo; the footer line is the only reference.
- **Don't** celebrate borrowing. Growth is celebrated only when a repayment plants the bean.
