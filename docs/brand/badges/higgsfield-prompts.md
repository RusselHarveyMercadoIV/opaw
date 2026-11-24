# Badges: Higgsfield prompts

Art for the 16 badges in `android/core/src/main/kotlin/app/opaw/core/Badges.kt` (design v5, screen 2f).
Model `gpt_image_2_6`, quality medium, 1:1, transparent background.

## Shared style (prepended to every prompt)

> Flat 2D cartoon achievement badge for a cute pet-care app, sticker style. A round medal: a thick warm orange (#FF7A1A) outer ring with a thin lighter orange highlight band, a clean white circular face, and one simple flat icon in the middle. Every shape has the same even, clean dark-brown (#3A2418) outline. Flat colour fills with only one soft shadow tone, no gradients, no gloss, no texture, no 3D, no glow. Rounded, chunky, friendly shapes; colours limited to orange, white, soft light grey, dark brown, with a small touch of the stated accent colour. Centred, front view, the medal fills about 85% of the frame. Isolated on a fully transparent background, nothing else in the image. No words, no letters.

## Icons

| id | badge | icon prompt |
|---|---|---|
| firstLog | First Paw Print | one big orange paw print |
| careStreak7 | Week of Love | an orange flame with a small white heart inside, the number 7 in chunky rounded dark-brown digits below it |
| careStreak30 | Devoted Human | a big orange flame with a small white heart inside, the number 30 in chunky rounded dark-brown digits below it, two small orange stars beside the flame |
| firstWalk | First Steps | an orange leash looped into a heart shape, with three small paw prints walking away from it |
| walks10 | Regular Rambler | a winding dotted path with small paw prints leading to a little orange flag, the number 10 in chunky rounded dark-brown digits |
| walks100 | Trail Legend | a rounded green hill with a winding path and paw prints up to an orange flag on top, the number 100 in chunky rounded dark-brown digits |
| km5Walk | 5K Pup | the text 5K in chunky rounded dark-brown characters with two orange paw prints and short speed lines |
| km10Walk | 10K Hound | the text 10K in chunky rounded dark-brown characters with a small orange lightning bolt and short speed lines |
| km50Month | 50 km Month | a cute calendar page with orange top binding showing the number 50 in chunky rounded dark-brown digits and a small paw print |
| km100Total | Century Paws | an orange trophy cup with a white paw print on it, the number 100 in chunky rounded dark-brown digits on its base |
| earlyBird | Early Bird | an orange half sun rising with rays, a small round cute bird with dot eyes sitting in front of it, soft light blue accent |
| walkStreak7 | Never Skip a Walk | a cute calendar page with orange top binding showing seven small orange tick marks in a row |
| squeakyClean | Squeaky Clean | a little white bathtub with round soap bubbles and a small yellow rubber duck, soft light blue accent |
| memoryKeeper | Memory Keeper | two overlapping instant photos tilted, the front one showing a small orange heart |
| healthCheck | Health Check | an orange heart with a white medical plus on it and a simple grey stethoscope curling around it |
| packLeader | Pack Leader | three orange paw prints clustered together with a small gold-orange crown above the middle one |

## Levels: bronze, silver, gold (2026-11-24)

Each badge levels up bronze → silver → gold, so the art carries no numbers (the target shows as text). The 16 single badges became 12 families; four number-free medals were generated for them:

| family | source | targets (bronze · silver · gold) | icon takes metal? |
|---|---|---|---|
| paw-prints | firstLog | 10 · 100 · 500 care logs | yes |
| care-streak | "big orange flame with a small white heart inside, two small orange stars beside the flame" | 7 · 30 · 100 days | no (flame stays orange) |
| walks | firstWalk | 10 · 50 · 200 walks | yes |
| long-walk | "rounded green hill with a winding grey path and small paw prints up to an orange flag" | 3 · 5 · 10 km in one go | yes |
| distance | "orange trophy cup with a white paw print, plain light grey base" | 25 · 100 · 500 km total | yes |
| busy-month | "two orange paw prints dashing forward, grey speed lines, small lightning bolt" | 20 · 50 · 100 km in a month | yes |
| early-bird | earlyBird | 5 · 20 · 50 walks before 7 am | no (sunrise) |
| walk-streak | walkStreak7 | 7 · 30 · 100 days | yes |
| squeaky-clean | squeakyClean | 5 · 15 · 30 baths | no (yellow duck) |
| memory-keeper | memoryKeeper | 10 · 50 · 200 memories | yes |
| health-check | healthCheck | 1 · 3 · 10 vet visits | no (heart) |
| pack-leader | packLeader | 5 · 15 · 30 friends | yes |

`source/` holds the orange 1024px masters; `tiers/` the 512px WebP medals. Metals are recoloured locally, not regenerated: `python3 make_tiers.py` (`run(src, outs, icon=True|False)`) maps the orange rim (and icon when `icon=True`) onto bronze #C27A3E, silver #B9C0C8, gold #F4BE2A ramps. Not yet earned: bronze medal, greyscale at 32% opacity, with a lock.
