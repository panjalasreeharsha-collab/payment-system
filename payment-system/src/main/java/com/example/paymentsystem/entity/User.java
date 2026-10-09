package com.example.paymentsystem.entity;

import jakarta.persistence.Column;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "wallet_users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallet_user_phone", columnNames = "phone_number"),
        @UniqueConstraint(name = "uk_wallet_user_upi", columnNames = "upi_id")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "upi_id", nullable = false, length = 100)
    private String upiId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String password;

    @OneToOne(mappedBy = "user", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private Wallet wallet;

    protected User() {
    }

    public User(String name, String phoneNumber, String upiId, String password) {
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.upiId = upiId;
        this.password = password;
        this.wallet = new Wallet(this);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getUpiId() {
        return upiId;
    }

    public void setUpiId(String upiId) {
        this.upiId = upiId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}