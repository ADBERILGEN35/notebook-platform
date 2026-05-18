---
name: Notebook Platform Design System
colors:
  surface: '#fcf8ff'
  surface-dim: '#dcd8e3'
  surface-bright: '#fcf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f2fc'
  surface-container: '#f0ecf6'
  surface-container-high: '#eae6f1'
  surface-container-highest: '#e4e1eb'
  on-surface: '#1b1b22'
  on-surface-variant: '#464553'
  inverse-surface: '#303037'
  inverse-on-surface: '#f3eff9'
  outline: '#777584'
  outline-variant: '#c8c4d5'
  surface-tint: '#544fc0'
  primary: '#1f108e'
  on-primary: '#ffffff'
  primary-container: '#3730a3'
  on-primary-container: '#a9a7ff'
  inverse-primary: '#c3c0ff'
  secondary: '#515f74'
  on-secondary: '#ffffff'
  secondary-container: '#d5e3fc'
  on-secondary-container: '#57657a'
  tertiary: '#511c00'
  on-tertiary: '#ffffff'
  tertiary-container: '#752c00'
  on-tertiary-container: '#fe9562'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#3b35a7'
  secondary-fixed: '#d5e3fc'
  secondary-fixed-dim: '#b9c7df'
  on-secondary-fixed: '#0d1c2e'
  on-secondary-fixed-variant: '#3a485b'
  tertiary-fixed: '#ffdbcc'
  tertiary-fixed-dim: '#ffb694'
  on-tertiary-fixed: '#351000'
  on-tertiary-fixed-variant: '#7a3003'
  background: '#fcf8ff'
  on-background: '#1b1b22'
  surface-variant: '#e4e1eb'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: '1'
    letterSpacing: 0.01em
  mono-sm:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '400'
    lineHeight: '1.5'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 48px
  container-max: 1280px
  gutter: 24px
---

## Brand & Style
The design system is engineered for deep focus and institutional trust. It blends the structural rigor of enterprise software with the ethereal, airy quality of modern productivity tools. The aesthetic is "Minimalist-Enterprise"—leveraging significant whitespace, a restrained palette, and razor-sharp execution.

The UI should evoke a sense of quiet power. By stripping away non-essential ornamentation, the design system allows user data and complex workflows to take center stage. Every interaction is designed to feel intentional and frictionless, mirroring the reliability of a high-end physical notebook while providing the speed of a modern SaaS engine.

## Colors
The color strategy prioritizes legibility and semantic clarity. The background utilizes `slate-50` to reduce eye strain compared to pure white, creating a "paper-like" canvas. 

Indigo is reserved for primary momentum—actions that move a workflow forward. Slate provides the structural foundation for navigation, borders, and secondary text. Accents are applied with high restraint, using low-saturation backgrounds (`-50` weight) for containers and high-contrast foregrounds for text to ensure WCAG AA compliance. Use color only when it provides meaningful information; otherwise, default to the neutral Slate scale.

## Typography
This design system employs a dual-font approach to balance personality and utility. **Manrope** is used for headlines to provide a refined, geometric character that feels modern and premium. **Inter** is the workhorse for all body copy and interface elements, chosen for its exceptional legibility in data-dense environments.

Generous line heights (1.5x to 1.6x for body) are mandatory to ensure the "calm" aesthetic. Hierarchy is established primarily through weight and color (Slate-900 for headings vs. Slate-600 for body) rather than dramatic size shifts. Use `mono-sm` for IDs, code snippets, or technical metadata to provide a distinct visual "mode" for data.

## Layout & Spacing
The layout philosophy is built on a strict 4px soft grid. Components should use `16px` (md) as the default internal padding. 

For enterprise dashboards, the system uses a **Fixed-Fluid Hybrid**:
- **Navigation:** Fixed 240px left sidebar.
- **Content:** Fluid center area with a `container-max` of 1280px to prevent line lengths from becoming unreadable.
- **Margins:** 24px (lg) margins on mobile, scaling to 48px (xl) on desktop to enhance the feeling of premium whitespace.

Vertical rhythm should be maintained by using multiples of 8px between sections. Larger gaps are encouraged between unrelated groups to reinforce cognitive clarity.

## Elevation & Depth
Depth is achieved through "Tonal Elevation" and sophisticated, diffused shadows. We avoid heavy blacks in shadows, opting for Slate-tinted blurs that feel integrated into the background.

- **Level 0 (Floor):** `slate-50` background.
- **Level 1 (Cards/Surface):** White background with a 1px border in `slate-200`.
- **Level 2 (Floating/Hover):** Subtle shadow (0px 4px 6px -1px rgba(15, 23, 42, 0.05)). Used for cards on hover.
- **Level 3 (Overlay):** Pronounced shadow (0px 10px 15px -3px rgba(15, 23, 42, 0.1)). Used for modals and dropdowns.

Use semi-transparent backdrops (`backdrop-blur`) for fixed headers to maintain a sense of place as the user scrolls.

## Shapes
The shape language is "Approachable Geometric." We use a consistent roundedness that softens the analytical nature of enterprise data.

- **Inputs & Small Buttons:** 8px (`radius_md`) provides a crisp, modern look.
- **Cards & Modals:** 12px (`radius_lg`) creates a clear containment boundary that feels premium.
- **Badges & Tags:** Always `radius_pill` to distinguish them from actionable buttons and static containers.

Borders must always be 1px wide. Avoid thicker strokes, as they disrupt the "clean" aesthetic and add unnecessary visual weight.

## Components
Consistent execution of components is vital for the "Trustworthy" pillar of the design system.

### Buttons
- **Primary:** Indigo-600 background, white text. No gradient. Solid 8px radius.
- **Secondary:** White background, Slate-200 border, Slate-900 text. 
- **Tertiary/Ghost:** No background or border. Slate-600 text, turns Slate-900 on hover.

### Badges (Status)
Pill-shaped with a subtle background (`-50` or `-100`) and high-contrast text (`-700`).
- **Low Risk/Success:** Emerald.
- **Medium Risk/Warning:** Amber.
- **High/Critical Risk:** Red.
- **Active/Neutral:** Indigo or Slate.

### Input Fields
Inputs use a white background with a `slate-200` border. On focus, the border transitions to `indigo-600` with a soft `indigo-100` outer glow (2px).

### Cards
Cards are the primary container. They use a white surface, a `slate-200` border, and 12px rounded corners. Header sections within cards should be separated by a subtle 1px bottom border.

### Contextual Components
- **Command Palette:** A center-aligned modal (Vercel-style) using `backdrop-blur` and Level 3 elevation for global navigation and search.
- **Data Tables:** Row-based layout with no vertical borders. Use 1px horizontal dividers and highlight rows on hover with `slate-50`.