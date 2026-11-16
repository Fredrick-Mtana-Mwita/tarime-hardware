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
