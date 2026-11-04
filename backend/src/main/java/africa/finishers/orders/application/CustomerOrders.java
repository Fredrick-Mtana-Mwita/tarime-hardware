package africa.finishers.orders.application;

import africa.finishers.catalogue.application.PurchasingCatalogue;
import africa.finishers.identity.application.CustomerAccounts;
import africa.finishers.identity.domain.PhoneNumber;
import africa.finishers.notifications.application.OrderNotifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static africa.finishers.orders.application.OrderModels.*;

@Service
public class CustomerOrders {
    private final PurchasingCatalogue catalogue;
    private final CustomerAccounts accounts;
    private final OrderRepository repository;
    private final OrderNotifications notifications;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public CustomerOrders(PurchasingCatalogue catalogue,CustomerAccounts accounts,OrderRepository repository,OrderNotifications notifications){
        this.catalogue=catalogue;this.accounts=accounts;this.repository=repository;this.notifications=notifications;
    }
    @Transactional(readOnly=true)
    public Quote quote(List<Selection> selections){return price(selections,false,null);}
    @Transactional(readOnly=true)
    public Quote quote(List<Selection> selections,Branch branch){return price(selections,false,branch);}
    private Quote price(List<Selection> selections,boolean lock,Branch branch){
        if(selections==null||selections.isEmpty()||selections.size()>50)throw new OrderProblem(400,"Choose between 1 and 50 items.");
        var quantities=new TreeMap<UUID,BigDecimal>();
        for(var selection:selections){
            if(selection==null||selection.variantId()==null||selection.quantity()==null)throw new OrderProblem(400,"Choose a product and quantity.");
            var q=selection.quantity();
            if(q.signum()<=0||q.compareTo(new BigDecimal("1000000"))>0||q.stripTrailingZeros().scale()>3)
                throw new OrderProblem(400,"Use a positive quantity with at most 3 decimal places.");
            if(quantities.put(selection.variantId(),q.setScale(3))!=null)throw new OrderProblem(400,"Combine repeated product options into one cart line.");
        }
        var products=catalogue.items(new ArrayList<>(quantities.keySet()),lock,branch==null?null:branch.name());
        if(products.size()!=quantities.size())throw new OrderProblem(409,"An item is unavailable or needs a shop quote. Update your cart.");
        String currency=products.getFirst().currency();
        var lines=new ArrayList<Line>();BigDecimal subtotal=BigDecimal.ZERO.setScale(2);
        for(var p:products){
            var q=quantities.get(p.variantId());
            if(!p.fractional()&&q.stripTrailingZeros().scale()>0)throw new OrderProblem(400,"Use whole quantities for "+p.name()+".");
            if(q.compareTo(p.available())>0)throw new OrderProblem(409,"Not enough stock for "+p.name()+". Reduce the quantity or remove it.");
            if(!currency.equals(p.currency()))throw new OrderProblem(400,"Items in one order must use the same currency.");
            BigDecimal total=p.price().multiply(q).setScale(2,RoundingMode.HALF_UP);
            subtotal=subtotal.add(total);
            if(subtotal.compareTo(new BigDecimal("99999999999999999.99"))>0)throw new OrderProblem(400,"Order value exceeds the supported limit.");
            lines.add(new Line(p.variantId(),p.productId(),p.name(),p.label(),p.sku(),p.unit(),p.fractional(),q,p.price(),currency,total,p.available()));
        }
        return new Quote(List.copyOf(lines),currency,subtotal);
    }
    @Transactional(timeout=20)
    public Order submit(String principal,UUID key,Submission raw){
        if(key==null)throw new OrderProblem(400,"A retry key is required.");
        Submission request=normalize(raw);
        UUID customerId=accounts.profile(principal).id();
        // Serialize a customer's submissions, including retries; no stock reservations are made here.
        repository.lockCustomer(customerId);
        String hash=fingerprint(request);
        var existing=repository.existing(customerId,key);
        if(existing.isPresent()){
            if(!existing.get().hash().equals(hash))throw new OrderProblem(409,"This retry key belongs to a different order request.");
            return repository.find(customerId,existing.get().id()).orElseThrow();
        }
        if(repository.submissionLimitReached(customerId))throw new OrderProblem(429,"Too many order requests. Please try again in an hour.");
        var quote=price(request.items().stream().map(i->new Selection(i.variantId(),i.quantity())).toList(),true,request.branch());
        for(var line:quote.items()){
            var reviewed=request.items().stream().filter(i->i.variantId().equals(line.variantId())).findFirst().orElseThrow();
            if(reviewed.expectedPrice().compareTo(line.unitPrice())!=0||!reviewed.currency().equals(line.currency()))
                throw new OrderProblem(409,"A price has changed. Refresh your cart and review the latest prices before submitting.");
        }
        UUID id=UUID.randomUUID();String reference="FPA-"+id.toString().replace("-","").toUpperCase(Locale.ROOT);
        repository.save(id,reference,customerId,key,hash,request,quote);
        notifications.submitted(customerId,id,reference);
        return repository.find(customerId,id).orElseThrow();
    }
    private Submission normalize(Submission r){
        if(r==null||r.branch()==null||r.fulfilment()==null||r.items()==null||r.items().isEmpty()||r.items().size()>50)throw new OrderProblem(400,"Choose a branch, your items and delivery or pickup.");
        String name=clean(r.name(),100),address=clean(r.address(),500),instructions=clean(r.instructions(),500),phone;
        if(name.length()<2||r.fulfilment()==Fulfilment.DELIVERY&&address.length()<5)throw new OrderProblem(400,"Enter your name and delivery address.");
        try{phone=PhoneNumber.normalize(r.phone());}catch(IllegalArgumentException|NullPointerException e){throw new OrderProblem(400,"Enter a valid contact phone number.");}
        var items=new ArrayList<ReviewedItem>();
        for(var i:r.items()){
            if(i==null||i.variantId()==null||i.quantity()==null||i.expectedPrice()==null||i.expectedPrice().signum()<0||i.expectedPrice().precision()>19||i.expectedPrice().scale()>2||i.currency()==null||!i.currency().matches("[A-Z]{3}"))
                throw new OrderProblem(400,"Review your cart prices and quantities.");
            try{items.add(new ReviewedItem(i.variantId(),i.quantity().setScale(3,RoundingMode.UNNECESSARY),i.expectedPrice().setScale(2,RoundingMode.UNNECESSARY),i.currency()));}
            catch(ArithmeticException e){throw new OrderProblem(400,"Check quantity and price precision.");}
        }
        items.sort(Comparator.comparing(ReviewedItem::variantId));
        return new Submission(List.copyOf(items),r.fulfilment(),name,phone,r.fulfilment()==Fulfilment.PICKUP?"":address,instructions,r.branch());
    }
    private String clean(String value,int max){
        String result=value==null?"":value.strip();
        if(result.length()>max||result.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new OrderProblem(400,"Contact details or instructions are too long or invalid.");
        return result;
    }
    private String fingerprint(Submission request){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    @Transactional(readOnly=true)
    public Order find(String principal,UUID id){return repository.find(accounts.profile(principal).id(),id).orElseThrow(()->new OrderProblem(404,"Order not found."));}
    @Transactional(readOnly=true)
    public OrderPage list(String principal,int page,int size){
        if(page<0||page>10000||size<1||size>50)throw new OrderProblem(400,"Choose a valid order page.");
        return repository.list(accounts.profile(principal).id(),page,size);
    }
}
