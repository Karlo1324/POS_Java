package com.saristore.model;
import java.math.BigDecimal;
public record Customer(int id, String name, String phone, String address, BigDecimal currentDebt) {}