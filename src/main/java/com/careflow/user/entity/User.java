package com.careflow.user.entity;

import com.careflow.common.enums.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    // 1. 호준님 파트: 대행사 승인 상태 필드
    @Column(length = 20)
    private String status;

    // 2. 🔥 컴파일 에러 예방을 위해 Long 타입으로 임시 선언! (DB 구조는 똑같이 매핑됩니다)
    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "address_detail", length = 100)
    private String addressDetail;
}