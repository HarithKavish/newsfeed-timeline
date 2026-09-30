# Governance

This repository is part of the **HarithKavish ecosystem** and is governed by
[HarithKavish Governance](https://github.com/HarithKavish/harithkavish-governance).

Governance is read from that repository. It is not copied here.

## Start here

Agents: read [AGENTS.md](AGENTS.md) first, then follow
[AGENT_BOOTSTRAP.md](https://github.com/HarithKavish/harithkavish-governance/blob/main/AGENT_BOOTSTRAP.md).

## This repository

- **Role:** application
- **Surface:** none (an Android APK; the news it reads is served by
  [Timeline](https://github.com/HarithKavish/Timeline) at timeline.harithkavish.com)
- **Production branch:** `main`
- **Development branch:** `development`
- **Last verified against governance:** 2026-09-30T17:05:00Z

## Especially applicable

- [REPOSITORY](https://github.com/HarithKavish/harithkavish-governance/blob/main/standards/REPOSITORY.md)
- [BRANCHING](https://github.com/HarithKavish/harithkavish-governance/blob/main/standards/BRANCHING.md)
- [DESIGN_SYSTEM](https://github.com/HarithKavish/harithkavish-governance/blob/main/standards/DESIGN_SYSTEM.md)
- [DEVELOPMENT](https://github.com/HarithKavish/harithkavish-governance/blob/main/standards/DEVELOPMENT.md)
- [SECURITY](https://github.com/HarithKavish/harithkavish-governance/blob/main/standards/SECURITY.md)

## Declared exceptions

### Design tokens are mirrored into Android resources

**What is deviated from:** Article 3 (Single Source of Truth) and
[DESIGN_SYSTEM](https://github.com/HarithKavish/harithkavish-governance/blob/main/standards/DESIGN_SYSTEM.md),
which require a surface to consume the shared foundations rather than hold a copy.

**Why:** the design system publishes a CSS custom-property distribution and JSX
components. An Android application can consume neither — there is no stylesheet
and no DOM. The colour, radius and spacing values therefore exist here a second
time, as `app/src/main/res/values/colors.xml`, `values-night/colors.xml` and
`dimens.xml`.

The copy is a transcription, not a second identity. Every value is the
design-system token converted to an Android unit (CSS `rgba()` to `#AARRGGBB`,
`rem` to `dp` at the 16px root), each one carrying the source value in a
comment, and the dark set is the system's own `:root[data-theme='dark']` swap
rather than a locally invented dark palette. No value in this repository was
chosen here.

**What would end it:** the design system publishing its foundations in a
format a native client can read — an Android resource artifact, or a
machine-readable token file (for example Style Dictionary JSON) that this
repository generates its resources from at build time. The second is the
cheaper of the two and would close this for any future native surface, not
only this one. Recorded as a gap in the *system*, per the standard's
"Selective Opt-Out" guidance.

### No shared shell, header or footer

**What is deviated from:** the shared component layer.

**Why:** those components are web components for a page. This surface is a
launcher panel with no page chrome — it is a feed rendered inside a window the
launcher owns. The foundations still apply and are used; only the shell does
not exist here to be consumed.

**What would end it:** nothing. This is the standard's intended
"disable a piece, not the system" case, recorded rather than left silent.
