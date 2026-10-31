package com.example.enterpriseassistant.repository;

import java.util.UUID;

import com.example.enterpriseassistant.entity.ChatInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatInteractionRepository extends JpaRepository<ChatInteraction, UUID> {
}
