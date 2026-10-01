package com.forge.backend.model;

import jakarta.persistance.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocaldateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder



