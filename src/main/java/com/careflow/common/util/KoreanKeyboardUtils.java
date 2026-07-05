package com.careflow.common.util;

public class KoreanKeyboardUtils {

    private KoreanKeyboardUtils() {
    }

    // 두벌식 자판 매핑 — 유니코드 한글 완성형(가~힣) 분해 순서(초성19·중성21·종성28)와 동일한 인덱스
    private static final String[] CHOSEONG = {
            "r", "R", "s", "e", "E", "f", "a", "q", "Q", "t",
            "T", "d", "w", "W", "c", "z", "x", "v", "g"
    };
    private static final String[] JUNGSEONG = {
            "k", "o", "i", "O", "j", "p", "u", "P", "h", "hk",
            "ho", "hl", "y", "n", "nj", "np", "nl", "b", "m", "ml", "l"
    };
    private static final String[] JONGSEONG = {
            "", "r", "R", "rt", "s", "sw", "sg", "e", "f", "fr",
            "fa", "fq", "ft", "fx", "fv", "fg", "a", "q", "qt", "t",
            "T", "d", "w", "c", "z", "x", "v", "g"
    };

    private static final int HANGUL_BASE = 0xAC00;
    private static final int HANGUL_LAST = 0xD7A3;

    /**
     * 한글 문자열을 두벌식 자판 그대로(한/영 전환 없이) 입력했을 때 실제로 찍히는 영문 문자열로 변환.
     * 예: "이리나" -&gt; "dlflsk"
     * 회원가입 시 사용자가 한/영 전환을 깜빡하고 이름을 그대로 비밀번호에 입력하는 경우를 잡아내기 위함.
     * 한글 완성형(가~힣) 범위 밖의 문자는 그대로 유지한다.
     */
    public static String toQwerty(String input) {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch >= HANGUL_BASE && ch <= HANGUL_LAST) {
                int code = ch - HANGUL_BASE;
                int cho = code / (21 * 28);
                int jung = (code % (21 * 28)) / 28;
                int jong = code % 28;
                result.append(CHOSEONG[cho]).append(JUNGSEONG[jung]).append(JONGSEONG[jong]);
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }
}
