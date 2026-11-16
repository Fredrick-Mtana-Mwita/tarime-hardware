package africa.finishers.reporting.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ReportModels {
    private ReportModels(){}
    /** Orders by the day they were sent. Value is the requested materials subtotal (delivery fees are agreed later). */
    public record OrderFigures(long submitted,BigDecimal requestedValue,long confirmed,long rejected,long cancelled) {}
    /** Sales are recognised on delivery or collection (OD-10); refunds count on the day the Owner approved them. */
    public record SalesFigures(long completedOrders,BigDecimal gross,BigDecimal refunds,BigDecimal net) {}
    /** Money by the day it was recorded; corrections and refunds by the day they were approved. */
    public record MoneyFigures(BigDecimal cash,BigDecimal mobileMoney,BigDecimal received,long receipts,BigDecimal reversed,BigDecimal refunded,BigDecimal net) {}
    public record Day(LocalDate date,BigDecimal requestedValue,BigDecimal completedSales,BigDecimal received,BigDecimal refunded) {}
    public record TopProduct(String product,String variant,String unit,BigDecimal quantity,BigDecimal value) {}
    public record SalesReport(LocalDate from,LocalDate to,String currency,OrderFigures orders,SalesFigures sales,MoneyFigures money,List<Day> days,List<TopProduct> topProducts) {}
    public record BalanceRow(UUID orderId,String reference,String customer,String phone,String status,BigDecimal total,BigDecimal netPaid,BigDecimal due,BigDecimal refundDue,long ageDays) {}
    public record BalancesReport(String currency,BigDecimal owedToShop,BigDecimal owedToCustomers,long orders,List<BalanceRow> rows) {}
    public record StockRow(UUID variantId,String product,String variant,String sku,String unit,BigDecimal onHand,BigDecimal reserved,BigDecimal available,
                           BigDecimal threshold,boolean lowStock,BigDecimal unitPrice,BigDecimal valueAtPrice,BigDecimal received,BigDecimal dispatched,BigDecimal released) {}
    public record StockReport(LocalDate from,LocalDate to,String branch,String currency,long items,long lowStock,BigDecimal valueAtPrice,List<StockRow> rows) {}
}
