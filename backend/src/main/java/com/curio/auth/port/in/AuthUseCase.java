package com.curio.auth.port.in;

import com.curio.auth.dto.AuthResponse;
import com.curio.auth.dto.LoginRequest;
import com.curio.auth.dto.LoginResponse;
import com.curio.auth.dto.RegisterRequest;
import com.curio.auth.dto.RegisterResponse;
import com.curio.auth.dto.ResendCodeRequest;
import com.curio.auth.dto.ResendCodeResponse;
import com.curio.auth.dto.VerifyCodeRequest;
import com.curio.auth.dto.VerifyCodeResponse;

/**
 * Inbound port for authentication use cases.
 * Controllers and other driving adapters depend on this interface,
 * not on the concrete AuthService implementation.
 */
public interface AuthUseCase {

    /**
     * Starts a password sign-up and emails a confirmation link. Never issues a
     * session, and answers the same whether or not the address has an account.
     */
    RegisterResponse register(RegisterRequest request);

    /** Opens a sign-up confirmation link: confirms the email and issues a session. */
    AuthResponse confirmEmail(String token);

    /** Mails a fresh confirmation link if (and only if) the account exists and is unconfirmed. */
    void resendConfirmation(String email, String language);

    /**
     * Step 1 of email/password login. On valid credentials this emails a one-time
     * verification code and returns a challenge (no session yet); the client then
     * calls {@link #verifyCode}. When email verification is disabled it returns the
     * session directly.
     */
    LoginResponse login(LoginRequest request);

    /** Step 2 of email/password login: validate the emailed code and issue a session. */
    VerifyCodeResponse verifyCode(VerifyCodeRequest request);

    /** Re-send a fresh verification code for an in-flight login challenge. */
    ResendCodeResponse resendCode(ResendCodeRequest request);

    AuthResponse googleLogin(String idToken);

    AuthResponse refreshToken(String refreshTokenValue);

    void logout(String refreshTokenValue);

    void requestPasswordReset(String email);

    void resetPassword(String token, String newPassword);
}
