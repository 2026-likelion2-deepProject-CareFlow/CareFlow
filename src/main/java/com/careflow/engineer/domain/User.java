package com.careflow.engineer.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table(name = "users")
public class User { // 임시 user 클래스 (삭제 예정)
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userid")
    private Long userId;

    private Role role;
}
