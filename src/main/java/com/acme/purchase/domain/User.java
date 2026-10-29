package com.acme.purchase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 系统用户。演示用明文口令，真实项目必须使用 BCrypt/Argon2 加盐哈希。
 */
@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, length = 32)
    private String displayName;

    @Column(nullable = false, length = 32)
    private String department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false, length = 64)
    private String password;

    protected User() {
        // JPA 需要无参构造
    }

    public User(String username, String displayName, String department, Role role, String password) {
        this.username = username;
        this.displayName = displayName;
        this.department = department;
        this.role = role;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDepartment() {
        return department;
    }

    public Role getRole() {
        return role;
    }

    public String getPassword() {
        return password;
    }
}
