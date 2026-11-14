package africa.finishers.finance.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class FinanceModels {
    private FinanceModels(){}
    public enum Method { CASH,MOBILE_MONEY }
    public enum Provider { MPESA,MIXX,AIRTEL_MONEY,HALOPESA }
    public enum Kind { REVERSAL,REFUND }
    public record PaymentRequest(UUID id,Method method,Provider provider,String reference,BigDecimal amount,String note) {}
    public record AdjustmentRequest(UUID id,Kind kind,UUID paymentId,BigDecimal amount,String reason,Method refundMethod,Provider refundProvider) {}
    public record Decision(String reference,String note) {}
    public record Receipt(UUID id,String receiptNumber,String method,String provider,String reference,BigDecimal amount,String currency,
                          String note,String recordedBy,Instant recordedAt,boolean reversed) {}
    public record Adjustment(UUID id,UUID orderId,String orderReference,String kind,UUID paymentId,String receiptNumber,BigDecimal amount,String reason,
                             String refundMethod,String refundProvider,String refundReference,String status,String requestedBy,Instant requestedAt,
                             String decidedBy,Instant decidedAt,String decisionNote) {}
    /** Amounts after approved adjustments only. {@code due} is what the customer still owes; {@code refundDue} is what the shop owes back. */
    public record Balance(String currency,BigDecimal total,BigDecimal paid,BigDecimal reversed,BigDecimal refunded,BigDecimal netPaid,
                          BigDecimal due,BigDecimal refundDue,String status) {
        public static Balance of(String orderStatus,String currency,BigDecimal total,BigDecimal paid,BigDecimal reversed,BigDecimal refunded){
            BigDecimal net=paid.subtract(reversed).subtract(refunded);
            BigDecimal due=total.subtract(net).max(BigDecimal.ZERO);
            BigDecimal refundDue=net.subtract(total).max(BigDecimal.ZERO);
            String status=refundDue.signum()>0?"REFUND_DUE"
                    :refunded.signum()>0?(net.signum()==0?"REFUNDED":"PARTLY_REFUNDED")
                    :net.signum()==0?"UNPAID"
                    :due.signum()==0?"PAID":"PART_PAID";
            return new Balance(currency,total,paid,reversed,refunded,net,due,refundDue,status);
        }
    }
    public record Ledger(UUID orderId,String reference,String orderStatus,String fulfilment,String customerName,String customerPhone,
                         Balance balance,List<Receipt> payments,List<Adjustment> adjustments) {}
    public record LedgerRow(UUID orderId,String reference,String orderStatus,String customerName,Instant createdAt,Balance balance) {}
    public record Page(List<LedgerRow> items,long total,int page,int size) {}
    public record DaySummary(LocalDate date,String currency,BigDecimal cash,BigDecimal mobileMoney,BigDecimal received,int payments,
                             BigDecimal reversed,BigDecimal refunded,BigDecimal net,BigDecimal completedSales,int completedOrders) {}
    public record CustomerPayments(Balance balance,List<Receipt> receipts) {}
}
