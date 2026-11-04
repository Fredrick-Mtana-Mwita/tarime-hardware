export interface Category { id: string; slug: string; name: string; description: string; imagePath: string; productCount: number }
export interface Product {
  id: string; sku: string; slug: string; name: string; brand: string; description: string
  categorySlug: string; categoryName: string; price: number | null; currency: string; unit: string
  imagePath: string; imageAlt: string; inStock: boolean; featured: boolean; sampleData: boolean
}
export interface Variant { id: string; sku: string; label: string; unit: string; price: number | null; currency: string; pricingMode: string; available: number; inStock: boolean; fractional: boolean }
export interface ProductPage { items: Product[]; total: number; page: number; size: number }
export interface Detail { product: Product; variants: Variant[]; images: { path: string; alt: string }[]; specifications: { label: string; value: string }[] }
