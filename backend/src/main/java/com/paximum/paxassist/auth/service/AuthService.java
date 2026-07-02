package com.paximum.paxassist.auth.service;

import com.paximum.paxassist.auth.domain.User;
import com.paximum.paxassist.auth.dto.LoginRequest;
import com.paximum.paxassist.auth.dto.LoginResponse;
import com.paximum.paxassist.auth.dto.RegisterRequest;
import com.paximum.paxassist.auth.dto.UserResponse;
import com.paximum.paxassist.auth.exception.ConflictException;
import com.paximum.paxassist.auth.exception.InvalidCredentialsException;
import com.paximum.paxassist.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Bu e-posta adresi zaten kayıtlı");
        }
        User user = new User(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.fullName(),
                "USER"
        );
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole());
        return LoginResponse.of(token, user);
    }
}
