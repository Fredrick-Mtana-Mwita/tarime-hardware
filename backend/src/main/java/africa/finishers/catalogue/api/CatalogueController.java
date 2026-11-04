package africa.finishers.catalogue.api;

import africa.finishers.catalogue.application.CatalogueReader;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static africa.finishers.catalogue.application.CatalogueModels.*;

@RestController
@RequestMapping("/api")
public class CatalogueController {
    private final CatalogueReader reader;
    public CatalogueController(CatalogueReader reader){this.reader=reader;}
    @GetMapping("/categories")
    public List<Category> categories(){return reader.categories();}
    @GetMapping("/products")
    public ProductPage products(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="") String category,
            @RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,
            @RequestParam(defaultValue="false") boolean inStock,@RequestParam(defaultValue="false") boolean featured,
            @RequestParam(defaultValue="featured") String sort,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="12") int size) {
        if(q.length()>150 || category.length()>100 || page<0 || page>100000 || size<1 || size>48
                || !Set.of("featured","name","price-asc","price-desc").contains(sort)
                || (minPrice!=null && minPrice.signum()<0) || (maxPrice!=null && maxPrice.signum()<0)
                || (minPrice!=null && maxPrice!=null && minPrice.compareTo(maxPrice)>0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid catalogue filters");
        return reader.products(new Filter(q.strip(),category.strip(),minPrice,maxPrice,inStock,featured,sort,page,size));
    }
    @GetMapping("/products/{id}")
    public Detail product(@PathVariable UUID id){return reader.product(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));}
}
