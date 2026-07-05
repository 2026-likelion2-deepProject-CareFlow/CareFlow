package com.careflow.common.util;

public class PhoneNumberUtils {

    private PhoneNumberUtils() {
    }

    // 회원가입 시 입력받은 전화번호를 DB에 하이픈 없이 저장하기 위한 정규화 (사업자번호는 하이픈 유지 대상이라 별개)
    public static String stripHyphens(String phone) {
        return phone == null ? null : phone.replace("-", "");
    }
}
