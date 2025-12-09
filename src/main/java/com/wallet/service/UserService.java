package com.wallet.service;

import com.wallet.dtos.UserDto;
import com.wallet.entity.User;
import com.wallet.exception.ResourceNotFoundException;
import com.wallet.repository.UserRepository;
import com.wallet.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final WalletService walletService;

/*
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
*/
    @Transactional
    public User findOrCreateUser(String googleId, String email, String name, String picture) {
        return userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElseGet(() -> createUser(googleId, email, name, picture));
    }

    private User createUser(String googleId, String email, String name, String picture) {
        String walletNumber = generateWalletNumber();

        User user = User.builder()
                .googleId(googleId)
                .email(email)
                .name(name)
                .picture(picture)
                .walletNumber(walletNumber)
                .isActive(true)
                .build();

        user = userRepository.save(user);

        // Create wallet for user
        walletService.createWallet(user);

        return user;
    }

    private String generateWalletNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();

        // Generate 13-digit wallet number
        for (int i = 0; i < 13; i++) {
            sb.append(random.nextInt(10));
        }

        String walletNumber = sb.toString();

        // Ensure uniqueness
        while (userRepository.existsByWalletNumber(walletNumber)) {
            walletNumber = generateWalletNumber();
        }

        return walletNumber;
    }

    public User findById(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public User findByWalletNumber(String walletNumber) {
        return userRepository.findByWalletNumber(walletNumber)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public UserDto toDto(User user) {
        return UserDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .picture(user.getPicture())
                .walletNumber(user.getWalletNumber())
                .build();
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new CustomUserDetails(user);
    }

}