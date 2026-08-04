package com.acmecorp.refunds;

import java.util.List;

/** Order refund processing for Acme Corp's storefront backend. */
public class RefundProcessor {

    // Low complexity (green, score ~2): early-return guard clauses only.
    public boolean validateOrder(Order order) {
        if (order == null) {
            return false;
        }
        if (order.items.isEmpty()) {
            return false;
        }
        return true;
    }

    // Medium complexity (yellow, score ~12): nested loop with an if/else-if
    // branch on one side, a plain loop on the other.
    public double calculateShippingCost(Order order) {
        double cost = 0;
        if (order.express) {
            for (Item item : order.items) {
                if (item.fragile) {
                    cost += 15;
                } else if (item.oversized) {
                    cost += 10;
                }
            }
        } else {
            for (Item item : order.items) {
                cost += 2;
            }
        }
        return cost;
    }

    // High complexity (red, score ~24): try/catch wrapping a loop with a
    // three-way if/else-if/else branch, one arm of which nests another
    // if/else-if/else, the other arm a switch.
    public void processRefundRequest(RefundRequest request) {
        try {
            if (request.amount > 0) {
                for (RefundItem item : request.items) {
                    if (item.reason == Reason.DEFECTIVE) {
                        if (item.quantity > 10) {
                            escalate(item);
                        } else if (item.underWarranty) {
                            autoApprove(item);
                        } else {
                            manualReview(item);
                        }
                    } else if (item.reason == Reason.WRONG_ITEM) {
                        autoApprove(item);
                    } else {
                        switch (item.status) {
                            case PENDING:
                                queue(item);
                                break;
                            case SHIPPED:
                                recall(item);
                                break;
                            default:
                                reject(item);
                        }
                    }
                }
            }
        } catch (RefundException e) {
            logError(e);
        }
    }

    private void escalate(RefundItem item) {}
    private void autoApprove(RefundItem item) {}
    private void manualReview(RefundItem item) {}
    private void queue(RefundItem item) {}
    private void recall(RefundItem item) {}
    private void reject(RefundItem item) {}
    private void logError(RefundException e) {}

    static class Order {
        boolean express;
        List<Item> items;
    }

    static class Item {
        boolean fragile;
        boolean oversized;
    }

    static class RefundRequest {
        double amount;
        List<RefundItem> items;
    }

    static class RefundItem {
        Reason reason;
        int quantity;
        boolean underWarranty;
        Status status;
    }

    enum Reason { DEFECTIVE, WRONG_ITEM, CHANGED_MIND }

    enum Status { PENDING, SHIPPED, DELIVERED }

    static class RefundException extends Exception {}
}
