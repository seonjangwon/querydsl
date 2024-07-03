package study.querydsl.repository;

import org.springframework.data.domain.Page;
import study.querydsl.dto.MemberSearchCondition;
import study.querydsl.dto.MemberTeamDTO;

import org.springframework.data.domain.Pageable;
import java.util.List;

public interface MemberRepositoryCustom {

    List<MemberTeamDTO> search(MemberSearchCondition condition);
    Page<MemberTeamDTO> searchPageSimple(MemberSearchCondition condition, Pageable pageable);
    Page<MemberTeamDTO> searchPageSimple2(MemberSearchCondition condition, Pageable pageable);
    Page<MemberTeamDTO> searchPageComplex(MemberSearchCondition condition, Pageable pageable);
}
