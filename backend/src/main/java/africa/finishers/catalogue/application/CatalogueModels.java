package africa.finishers.catalogue.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CatalogueModels {
    private CatalogueModels() {}
    public record Category(UUID id,String slug,String name,String description,String imagePath,long productCount) {}
    public record Product(UUID id,String sku,String slug,String name,String brand,String description,
                          String categorySlug,String categoryName,BigDecimal price,String currency,String unit,
                          String imagePath,String imageAlt,boolean inStock,boolean featured,boolean sampleData) {}
    public record Variant(UUID id,String sku,String label,String unit,BigDecimal price,String currency,
                          String pricingMode,BigDecimal available,boolean inStock,boolean fractional) {}
    public record Image(String path,String alt) {}
    public record Specification(String label,String value) {}
    public record Detail(Product product,List<Variant> variants,List<Image> images,List<Specification> specifications) {}
    public record ProductPage(List<Product> items,long total,int page,int size) {}
    public record Filter(String query,String category,BigDecimal minPrice,BigDecimal maxPrice,
                         boolean inStock,boolean featured,String sort,int page,int size) {}
}
