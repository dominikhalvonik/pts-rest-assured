package sk.testlab.cvicenie04.model;

/** Heslo je potrebné na získanie tokenu, nikdy nie na vypisovanie do konzoly. */
public record FixtureAccount(String workspace, String email, String password) {
    public FixtureAccount {
        if (!isRunWorkspace(workspace))
            throw new IllegalArgumentException("Očakáva sa pôvodný workspace run- + 24 hex znakov.");
        if (email == null || email.isBlank() || !email.contains("@"))
            throw new IllegalArgumentException("Chýba platný e-mail fixture účtu.");
        if (password == null || password.isBlank())
            throw new IllegalArgumentException("Chýba heslo fixture účtu.");
    }
    public static boolean isRunWorkspace(String code) {
        return code != null && code.matches("run-[a-f0-9]{24}");
    }
    @Override public String toString() { return "FixtureAccount[workspace=" + workspace + ", credentials=REDACTED]"; }
}
