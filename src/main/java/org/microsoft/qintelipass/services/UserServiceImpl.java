package org.microsoft.qintelipass.services;

import lombok.extern.slf4j.Slf4j;
import org.microsoft.qintelipass.dtos.UserDTO;
import org.microsoft.qintelipass.enums.UserStatus;
import org.microsoft.qintelipass.models.User;
import org.microsoft.qintelipass.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserCacheService userCacheService;

    public UserServiceImpl(UserRepository userRepository, UserCacheService userCacheService) {
        this.userRepository = userRepository;
        this.userCacheService = userCacheService;
    }

    @Override
    public User getUserById(Long userId) {
        if (userId == null) {
            return null;
        }

        // Authorization-sensitive user state is database authoritative. A cached NORMAL
        // value must never keep a deactivated account authenticated after a Redis write failure.
        return userRepository.findById(userId).orElse(null);
    }

    @Override
    public User getUserByPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            return null;
        }

        // Authentication needs the password hash, which is intentionally absent from cached UserDTO data.
        log.debug("Fetching credential-bearing user from database by phone");
        Optional<User> userOpt = userRepository.findByPhone(phone);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            userCacheService.cacheUser(UserDTO.fromUser(user));
            return user;
        }
        return null;
    }

    @Override
    public User getUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        log.debug("Fetching credential-bearing user from database by email");
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }
        return null;
    }

    @Override
    public User getUserByWechatOpenId(String wechatOpenId) {
        return null;
    }

    @Override
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public void saveUser(User user) {
        if (user == null) {
            return;
        }

        User savedUser = userRepository.saveAndFlush(user);
        log.info("User saved to database: {}", savedUser.getId());

        userCacheService.cacheUser(UserDTO.fromUser(savedUser));
        log.debug("User cached: {}", savedUser.getId());
    }

    @Override
    @Transactional
    public boolean deactivateUser(Long userId) {
        if (userId == null) {
            return false;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return false;
        }

        User user = userOpt.get();
        if (UserStatus.DEACTIVATED.equals(user.getStatus())) {
            return false;
        }

        user.setStatus(UserStatus.DEACTIVATED);
        User savedUser = userRepository.saveAndFlush(user);

        userCacheService.cacheUser(UserDTO.fromUser(savedUser));

        return true;
    }

    @Override
    public boolean isUserDeactivated(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = getUserById(userId);
        return user != null && UserStatus.DEACTIVATED.equals(user.getStatus());
    }

    @Override
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        Optional<User> userOpt = userRepository.findByName(username);
        return userOpt.orElse(null);
    }
}
