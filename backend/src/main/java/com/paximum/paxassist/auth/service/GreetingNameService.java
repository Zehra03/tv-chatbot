package com.paximum.paxassist.auth.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paximum.paxassist.auth.repository.UserRepository;

/**
 * Resolves the short first name the assistant addresses a logged-in user by.
 *
 * <p>{@code display_name} is a single free-text field the user typed at registration, so it can be
 * a full name ("Berfin Deniz Doğan") — only the first word is used, since that is what reads
 * naturally in a greeting. It is also user-controlled text that ends up inside the system prompt,
 * so {@link #sanitize} keeps only a plain name-shaped token: anything carrying instructions,
 * markup or newlines is dropped and the caller is greeted without a name instead.
 */
@Service
public class GreetingNameService {

    /** Long enough for any real first name; a longer token is not a name and is discarded. */
    private static final int MAX_LENGTH = 32;

    private final UserRepository userRepository;

    public GreetingNameService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * @param userId the owner of the session, or null for a guest
     * @return the first name to greet by, or empty for a guest / unknown or unusable display name
     */
    @Transactional(readOnly = true)
    public Optional<String> firstNameOf(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return userRepository.findById(userId)
                .map(user -> user.getDisplayName())
                .flatMap(GreetingNameService::sanitize);
    }

    private static Optional<String> sanitize(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return Optional.empty();
        }
        String firstWord = displayName.strip().split("\\s+")[0];
        if (firstWord.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        // Letters (any alphabet, so Turkish characters pass), plus the apostrophe/hyphen real names
        // carry. Anything else means this is not a name we should paste into the prompt.
        if (!firstWord.matches("[\\p{L}'-]+")) {
            return Optional.empty();
        }
        return Optional.of(firstWord);
    }
}
