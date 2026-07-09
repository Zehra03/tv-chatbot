package com.paximum.paxassist.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.paximum.paxassist.auth.domain.Role;
import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.dto.AuthResponseDto;
import com.paximum.paxassist.auth.dto.LoginRequestDto;
import com.paximum.paxassist.auth.dto.RefreshRequestDto;
import com.paximum.paxassist.auth.dto.RegisterRequestDto;
import com.paximum.paxassist.auth.exception.EmailAlreadyExistsException;
import com.paximum.paxassist.auth.exception.InvalidRefreshTokenException;
import com.paximum.paxassist.auth.repository.UserRepository;
import com.paximum.paxassist.auth.security.JwtService;
import com.paximum.paxassist.auth.service.RefreshTokenService.RotatedTokens;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, passwordEncoder, authenticationManager, jwtService, refreshTokenService);
    }

    @Test
    void register_savesHashedPasswordAndReturnsToken() {
        RegisterRequestDto request = new RegisterRequestDto("new@example.com", "plain-password", "New User");
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(jwtService.generateToken("new@example.com", "USER")).thenReturn("jwt-token");
        when(refreshTokenService.issue(any(User.class))).thenReturn("refresh-token");

        AuthResponseDto response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.USER);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().id()).isEqualTo("1");
        assertThat(response.user().email()).isEqualTo("new@example.com");
        assertThat(response.user().name()).isEqualTo("New User");
    }

    @Test
    void register_throwsWhenEmailAlreadyRegistered() {
        RegisterRequestDto request = new RegisterRequestDto("dup@example.com", "plain-password", null);
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_authenticatesAndReturnsToken() {
        LoginRequestDto request = new LoginRequestDto("user@example.com", "correct-password");
        User user = User.builder()
                .id(2L)
                .email("user@example.com")
                .passwordHash("hashed")
                .displayName("Existing User")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("user@example.com", "ADMIN")).thenReturn("jwt-token");
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");

        AuthResponseDto response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().id()).isEqualTo("2");
        assertThat(response.user().name()).isEqualTo("Existing User");
    }

    @Test
    void login_propagatesAuthenticationFailure() {
        LoginRequestDto request = new LoginRequestDto("user@example.com", "wrong-password");
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager)
                .authenticate(any());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verifyNoInteractions(jwtService);
    }

    @Test
    void refresh_rotatesTokenAndMintsNewAccessToken() {
        User user = User.builder()
                .id(3L)
                .email("user@example.com")
                .passwordHash("hashed")
                .displayName("Existing User")
                .role(Role.USER)
                .build();
        when(refreshTokenService.rotate("old-refresh"))
                .thenReturn(new RotatedTokens(user, "new-refresh"));
        when(jwtService.generateToken("user@example.com", "USER")).thenReturn("new-jwt");

        AuthResponseDto response = authService.refresh(new RefreshRequestDto("old-refresh"));

        assertThat(response.token()).isEqualTo("new-jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        assertThat(response.user().id()).isEqualTo("3");
        assertThat(response.user().email()).isEqualTo("user@example.com");
    }

    @Test
    void refresh_propagatesInvalidTokenFailure() {
        when(refreshTokenService.rotate("bad-refresh")).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequestDto("bad-refresh")))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(jwtService);
    }

    @Test
    void logout_revokesAllRefreshTokensForUser() {
        authService.logout(7L);

        verify(refreshTokenService).revokeAllForUser(7L);
    }
}
