package com.android.settings.biometrics.face;

public final class FacePoseEnrollmentStateTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        FacePoseEnrollmentState s = new FacePoseEnrollmentState();
        check(FacePoseEnrollmentState.bucketForHelp(1101) == 0, "first bucket");
        check(FacePoseEnrollmentState.bucketForHelp(1125) == 24, "last bucket");
        check(FacePoseEnrollmentState.bucketForHelp(1100) == -1, "lower bound");
        check(FacePoseEnrollmentState.bucketForHelp(1126) == -1, "large angle is not a cell");
        for (int i = 0; i < 8; i++) s.onHelp(1101);
        check(!s.isInFrame(), "center must be seen before the guide enters good state");
        check(s.onHelp(1113), "fresh center");
        check(s.isInFrame(), "real center after stable samples enables grid");
        check(!s.onHelp(1113), "duplicates never increment acquired count");
        for (int i = 0; i < 9; i++) s.onHelp(4);
        check(s.isInFrame(), "single bad samples must not flicker grid");
        s.onHelp(4);
        check(!s.isInFrame(), "persistent out-of-frame hides grid");
        for (int i = 0; i < 5; i++) s.onHelp(1113);
        check(!s.isInFrame(), "good samples debounce");
        s.onHelp(1113);
        check(s.isInFrame(), "six good samples restore grid");
        for (int i = 1101; i <= 1125; i++) s.onHelp(i);
        check(!s.isTerminal(), "all 25 UI cells cannot complete enrollment");
        boolean[] copy = s.acquiredCells();
        copy[0] = false;
        check(s.acquiredCells()[0], "callers cannot mutate presentation state");
        s.finish();
        check(s.isTerminal(), "native terminal event closes presentation");
        FacePoseEnrollmentState error = new FacePoseEnrollmentState();
        error.finish();
        check(!error.onHelp(1101) && !error.acquiredCells()[0], "late help after error ignored");
        FacePoseEnrollmentState unrelated = new FacePoseEnrollmentState();
        unrelated.onHelp(1000);
        check(!unrelated.isInFrame() && !unrelated.isTerminal(), "unknown HAL code ignored");
        System.out.println("Face pose callback state: PASS");
    }
}
