package com.ninabit.bono.modules.popup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {
    List<Popup> findByActivoTrueOrderByOrdenAsc();

    List<Popup> findAllByOrderByOrdenAsc();
}
