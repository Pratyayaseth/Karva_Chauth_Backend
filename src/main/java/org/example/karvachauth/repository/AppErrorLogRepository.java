package org.example.karvachauth.repository;

import org.example.karvachauth.entity.AppErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppErrorLogRepository extends JpaRepository<AppErrorLog,Long> {
}
