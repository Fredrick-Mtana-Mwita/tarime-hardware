package africa.finishers.catalogue.application;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static africa.finishers.catalogue.application.CatalogueModels.*;

/** Public read projection; operational catalogue/inventory writes remain separate use cases. */
public interface CatalogueReader {
    List<Category> categories();
    ProductPage products(Filter filter);
    Optional<Detail> product(UUID id);
}
