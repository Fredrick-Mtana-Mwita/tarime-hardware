/**
 * Pictures for categories that have no photo of their own yet. A category's own imagePath always wins.
 *
 * PLACEHOLDER: free stock photos from Pexels (https://www.pexels.com/license/, free to use, no attribution
 * required), saved into public/images. Replace them with the shop's own photos when it has them.
 */
const fallback: Record<string, string> = {
  'power-tools-machinery': '/images/cat-power-tools.jpg', // Pexels 30486981
  'fasteners-fixings': '/images/cat-fasteners.jpg', // Pexels 39785074
  'steel-metal-products': '/images/cat-steel.jpg', // Pexels 36003982
  'doors-windows-accessories': '/images/cat-doors-windows.jpg', // Pexels 40653437
  'ceilings-partitions': '/images/cat-ceilings.jpg', // Pexels 31735047
  'welding-fabrication': '/images/cat-welding.jpg', // Pexels 4561617
  'security-safety-equipment': '/images/cat-safety.jpg', // Pexels 34442873
  'adhesives-sealants': '/images/cat-adhesives.jpg', // Pexels 6124242
  'gardening-outdoor': '/images/cat-gardening.jpg', // Pexels 3971211
  'cleaning-maintenance': '/images/cat-cleaning.jpg', // Pexels 5217889
}

/** The picture path for a category, or '' when there is none. */
export function categoryImage(slug: string, imagePath: string): string {
  return imagePath || fallback[slug] || ''
}

/** Wide covers (1100 x 760) for the home page category tiles. PLACEHOLDER: free Pexels photos, replace with the shop's own. */
const covers: Record<string, string> = {
  'power-tools-machinery': '/images/cat-power-tools-wide.jpg', // Pexels 30413428
  'fasteners-fixings': '/images/cat-fasteners-wide.jpg', // Pexels 5279361
  'steel-metal-products': '/images/cat-steel-wide.jpg', // Pexels 36003992
  'doors-windows-accessories': '/images/cat-doors-windows-wide.jpg', // Pexels 279810
  'ceilings-partitions': '/images/cat-ceilings-wide.jpg', // Pexels 7166945
  'welding-fabrication': '/images/cat-welding-wide.jpg', // Pexels 15888226
  'security-safety-equipment': '/images/cat-safety-wide.jpg', // Pexels 38070
  'adhesives-sealants': '/images/cat-adhesives-wide.jpg', // Pexels 6124242
  'gardening-outdoor': '/images/cat-gardening-wide.jpg', // Pexels 3971211
  'cleaning-maintenance': '/images/cat-cleaning-wide.jpg', // Pexels 3177257
}

/** The cover photo for a category tile: its own photo if it has one, else a placeholder photo, else ''. */
export function categoryCover(slug: string, imagePath: string): string {
  return imagePath || covers[slug] || ''
}
