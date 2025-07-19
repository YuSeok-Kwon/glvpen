package com.kepg.BaseBallLOCK.modules.gameMode.simulationMode.domain;

public class BaseState {
    public boolean firstBase = false;
    public boolean secondBase = false;
    public boolean thirdBase = false;

    // 각 루 주자의 출루 방식 저장
    public String firstBaseType = "";
    public String secondBaseType = "";
    public String thirdBaseType = "";
    
    // 타격 결과(outcome)에 따라 주자 진루 및 점수 계산
    public int advanceBases(String outcome) {
        int score = 0;

        if ("홈런".equals(outcome)) {
            score += countRunnersOnBase() + 1;
            clearBases();
            return score;
        }

        if ("3루타".equals(outcome)) {
            if (thirdBase) score++;
            if (secondBase) score++;
            if (firstBase) score++;

            thirdBase = true;
            secondBase = false;
            firstBase = false;

            thirdBaseType = "3루타";
            secondBaseType = null;
            firstBaseType = null;

            return score;
        }

        if ("2루타".equals(outcome)) {
            if (thirdBase) score++;
            if (secondBase) score++;
            if (firstBase) {
                thirdBase = true;
                thirdBaseType = firstBaseType;
            }

            secondBase = true;
            secondBaseType = "2루타";

            firstBase = false;
            firstBaseType = null;

            return score;
        }

        if ("안타".equals(outcome)) {
            if (thirdBase) score++;
            if (secondBase) {
                thirdBase = true;
                thirdBaseType = secondBaseType;
            } else {
                thirdBase = false;
                thirdBaseType = null;
            }

            if (firstBase) {
                secondBase = true;
                secondBaseType = firstBaseType;
            } else {
                secondBase = false;
                secondBaseType = null;
            }

            firstBase = true;
            firstBaseType = "안타";

            return score;
        }

        if ("볼넷".equals(outcome)) {
            if (firstBase) {
                if (secondBase) {
                    if (thirdBase) score++; // 밀어내기
                    thirdBase = secondBase;
                    thirdBaseType = secondBaseType;
                }
                secondBase = firstBase;
                secondBaseType = firstBaseType;
            }

            firstBase = true;
            firstBaseType = "볼넷";

            return score;
        }

        return 0;
    }

    // 모든 루 초기화 (주자 제거)
    private void clearBases() {
        firstBase = false; secondBase = false; thirdBase = false;
        firstBaseType = ""; secondBaseType = ""; thirdBaseType = "";
    }
    
    // 현재 루에 있는 주자 수 계산
    private int countRunnersOnBase() {
        int count = 0;
        if (firstBase) count++;
        if (secondBase) count++;
        if (thirdBase) count++;
        return count;
    }

}
