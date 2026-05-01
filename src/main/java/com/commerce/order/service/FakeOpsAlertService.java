package com.commerce.order.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class FakeOpsAlertService implements OpsAlertService {

    private final List<Long> alertedOrderIds = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void alertPaymentFailed(Long orderId) {
        alertedOrderIds.add(orderId);
    }

    public List<Long> getAlertedOrderIds() {
        return Collections.unmodifiableList(alertedOrderIds);
    }

    public void reset() {
        alertedOrderIds.clear();
    }
}