// Media for the interior and exterior design page.
//
// Interior pictures are the inspiration images supplied by the client on 12 November 2026.
// Exterior pictures were generated with Higgsfield. Until scripts/fetch-design-media.ps1 has
// saved them into frontend/public/images/design as small JPEG files, the page loads the
// original 3 MB PNG files from `remote`, which is slow on mobile data.
// Animation clips are optional: when interior.mp4 / exterior.mp4 exist in that folder they
// play over the still picture; otherwise the still picture is shown with a slow drift.

const generated = 'https://d8j0ntlcm91z4.cloudfront.net/user_3JSNAUiBU29RqcNYveF532JGej2/'

export const heroClips = {
  interior: {
    video: '/images/design/interior.mp4',
    poster: '/images/design-2.jpg',
    posterRemote: undefined as string | undefined,
    alt: 'Living room with a layered ceiling, chandelier and marble television wall',
  },
  exterior: {
    video: '/images/design/exterior.mp4',
    poster: '/images/design/exterior-1.jpg',
    posterRemote: generated + 'hf_20261112_112916_8d003157-49b6-43fd-8a9a-f29e7a3251cd.png' as string | undefined,
    alt: 'Two-storey house at dusk with a paved driveway and lit entrance',
  },
}

export const interiorIdeas = [
  { image: '/images/design-2.jpg', width: 736, height: 1104, title: 'Layered living room ceiling', detail: 'Gypsum tray ceiling, cove lighting and a marble TV wall' },
  { image: '/images/design-1.jpg', width: 736, height: 1104, title: 'Bedroom with lit arches', detail: 'Fluted wall panels, arch lighting and a brass pendant' },
  { image: '/images/design-3.jpg', width: 736, height: 1104, title: 'Warm modern kitchen', detail: 'Wood accents, a breakfast bar and under-cabinet lighting' },
  { image: '/images/design-4.jpg', width: 640, height: 640, title: 'Statement ceiling', detail: 'Marble-effect centre, wood slats and hidden LED strips' },
  { image: '/images/design-5.jpg', width: 600, height: 752, title: 'Built-in TV wall', detail: 'Fluted wood panels, open shelving and a floating cabinet' },
  { image: '/images/design-6.jpg', width: 736, height: 716, title: 'Classic wall panelling', detail: 'Wall mouldings with gold trim and a fluted centre panel' },
  { image: '/images/design-7.jpg', width: 735, height: 811, title: 'Lit feature panels', detail: 'Backlit frames, sculpted details and a decorative cornice' },
  { image: '/images/design-8.jpg', width: 736, height: 1104, title: 'Backlit circle wall', detail: 'A glowing round feature wall with slat panels and niches' },
  { image: '/images/design-9.jpg', width: 300, height: 300, title: 'Pendant lighting', detail: 'Decorative filament bulbs hung over a counter' },
]

export const exteriorIdeas = [
  { image: '/images/design/exterior-1.jpg', remote: generated + 'hf_20261112_112916_8d003157-49b6-43fd-8a9a-f29e7a3251cd.png', alt: 'Two-storey house at dusk with a paved driveway and lit entrance', title: 'Two-storey family home', detail: 'Stone-coated roof tiles, painted walls, cladding accents and paving blocks' },
  { image: '/images/design/exterior-2.jpg', remote: generated + 'hf_20261112_113054_8f3b5fc6-087e-4454-800e-f897bab2e978.png', alt: 'Single-storey bungalow in daylight with a stone feature wall and veranda', title: 'Modern bungalow', detail: 'Textured paint, a stone feature wall, roofing sheets and a tiled veranda' },
  { image: '/images/design/exterior-3.jpg', remote: generated + 'hf_20261112_113054_e0a5b542-b303-497e-a4ba-58824676cb3a.png', alt: 'Covered patio with a wooden pergola, garden lights and a paved path', title: 'Outdoor living', detail: 'Pergola, outdoor tiles, garden lighting and a painted perimeter wall' },
]
