package com.br.spbemestar.service;

import com.br.spbemestar.dto.CreateOperatorRequest;
import com.br.spbemestar.dto.OperatorItem;
import com.br.spbemestar.exception.OperatorAlreadyExistsException;
import com.br.spbemestar.exception.OperatorChangeNotAllowedException;
import com.br.spbemestar.exception.ResourceNotFoundException;
import com.br.spbemestar.model.AppUser;
import com.br.spbemestar.model.UserRole;
import com.br.spbemestar.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Equipe do órgão público, gerida pelo administrador. Ninguém é apagado: desativar preserva o histórico. A conta
 * do CEO não é da equipe: o administrador não a vê, não cria outra nem a desativa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorService {

    private final AppUserRepository users;
    private final AuthService auth;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<OperatorItem> list() {
        return users.findAll(Sort.by("active").descending().and(Sort.by("name"))).stream()
                .filter(user -> user.getRole() != UserRole.CEO)
                .map(OperatorItem::from).toList();
    }

    @Transactional
    public OperatorItem create(Long adminId, CreateOperatorRequest request) {
        if (request.role() == UserRole.CEO) {
            throw new OperatorChangeNotAllowedException("A conta do CEO não é cadastrada pela equipe.");
        }
        String email = AppUser.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new OperatorAlreadyExistsException(email);
        }
        AppUser user = auth.createUser(email, request.name().strip(), request.role());
        // sem o e-mail no log, como no login: o id basta para a auditoria
        log.info("Administrador {} cadastrou o usuário {} ({})", adminId, user.getId(), user.getRole());
        return OperatorItem.from(user);
    }

    @Transactional
    public OperatorItem setActive(Long adminId, Long userId, boolean active) {
        if (adminId.equals(userId) && !active) {
            // também garante que sempre sobra um administrador ativo: quem desativa continua ativo
            throw new OperatorChangeNotAllowedException("Você não pode desativar o próprio usuário.");
        }
        AppUser user = users.findById(userId).filter(u -> u.getRole() != UserRole.CEO)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário", userId));
        if (active) {
            user.activate();
        } else if (user.isActive()) {
            user.deactivate(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        }
        log.info("Administrador {} {} o usuário {}", adminId, active ? "reativou" : "desativou", userId);
        return OperatorItem.from(user);
    }
}
