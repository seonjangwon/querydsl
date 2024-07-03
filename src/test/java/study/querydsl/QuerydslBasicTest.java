package study.querydsl;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import study.querydsl.dto.MemberDTO;
import study.querydsl.dto.QMemberDTO;
import study.querydsl.dto.UserDTO;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.QTeam;
import study.querydsl.entity.Team;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.*;
import static study.querydsl.entity.QTeam.*;

@SpringBootTest
@Transactional
//@RequiredArgsConstructor
public class QuerydslBasicTest {

    @Autowired
    EntityManager em;
//    @PersistenceContext
//    EntityManager em;

    JPAQueryFactory queryFactory;

    @BeforeEach
    public void before() {
        Team teamA = new Team("teamA");
        Team teamB = new Team("teamB");
        em.persist(teamA);
        em.persist(teamB);

        Member member1 = new Member("member1", 10 , teamA);
        Member member2 = new Member("member2", 20 , teamA);

        Member member3 = new Member("member3", 30 , teamB);
        Member member4 = new Member("member4", 40 , teamB);
        em.persist(member1);
        em.persist(member2);
        em.persist(member3);
        em.persist(member4);
        queryFactory = new JPAQueryFactory(em);
    }

    @Test
    public void startJPQL(){
        // member1을 찾아라
        String qlString =
                "select m from Member m " +
                "where m.username = :username";
        Member findMember = em.createQuery(qlString, Member.class)
                .setParameter("username", "member1")
                .getSingleResult();

        assertThat(findMember.getUsername()).isEqualTo("member1");

    }

    @Test
    public void startQuerydsl() {


        QMember m = new QMember("m");
        QMember m2 = member;

        Member findMember = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void search(){
        Member findMember = queryFactory
                .selectFrom(member)
                .where(
                        member.username.eq("member1") // and 말고 ,(콤마)가능
                                .and(member.age.eq(10)))
                .fetchOne();

        assertThat(findMember.getUsername()).isEqualTo("member1");
    }

    @Test
    public void resultFetch(){
//        List<Member> fetch = queryFactory
//                .selectFrom(member)
//                .fetch();
//
//        Member fetchOne = queryFactory.selectFrom(member).fetchOne();
//
//        Member fetchFirst = queryFactory.selectFrom(member).fetchFirst();

        QueryResults<Member> results = queryFactory.selectFrom(member).fetchResults();
        results.getTotal();
        List<Member> contents = results.getResults();

        long total = queryFactory.selectFrom(member).fetchCount();
    }

    /**
     * 회원 정렬 순서
     * 1. 나일 내림(desc)
     * 2. 이름 올림 asc
     * 단 이름이 없으면 마지막에 출력 nulls last
     **/
    @Test
    public void sort(){
        em.persist(new Member(null, 100));
        em.persist(new Member("member5", 100));
        em.persist(new Member("member6", 100));

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(100))
                .orderBy(member.age.desc(), member.username.asc().nullsLast())
                .fetch();

        Member member5 = result.get(0);
        Member member6 = result.get(1);
        Member memberNull = result.get(2);

        assertThat(member5.getUsername()).isEqualTo("member5");
        assertThat(member6.getUsername()).isEqualTo("member6");
        assertThat(memberNull.getUsername()).isNull();

    }

    @Test
    public void paging() {
        List<Member> result = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetch();

        assertThat(result.size()).isEqualTo(2);


    }

    @Test
    public void paging2() {
        QueryResults<Member> queryResults = queryFactory
                .selectFrom(member)
                .orderBy(member.username.desc())
                .offset(1)
                .limit(2)
                .fetchResults();

        assertThat(queryResults.getTotal()).isEqualTo(4);
        assertThat(queryResults.getLimit()).isEqualTo(2);
        assertThat(queryResults.getOffset()).isEqualTo(1);


    }

    @Test
    public void aggregation(){
        List<Tuple> result = queryFactory
                .select(
                        member.count(),
                        member.age.sum(),
                        member.age.avg(),
                        member.age.max(),
                        member.age.min()
                )
                .from(member)
                .fetch();

        Tuple tuple = result.get(0);

        assertThat(tuple.get(member.count())).isEqualTo(4);
        assertThat(tuple.get( member.age.sum())).isEqualTo(100);
        assertThat(tuple.get(member.age.avg())).isEqualTo(25);
        assertThat(tuple.get( member.age.max())).isEqualTo(40);
        assertThat(tuple.get(member.age.min())).isEqualTo(10);



    }

    /**
     * 팀의 이름과 각 팀의 평균 연령을 구해라.
     */
    @Test
    public void group() {
        List<Tuple> result = queryFactory
                .select(team.name, member.age.avg())
                .from(member)
                .join(member.team, team)
                .groupBy(team.name)
                .fetch();

        Tuple teamA = result.get(0);
        Tuple teamB = result.get(1);

        assertThat(teamA.get(team.name)).isEqualTo("teamA");
        assertThat(teamA.get(member.age.avg())).isEqualTo(15);

        assertThat(teamB.get(team.name)).isEqualTo("teamB");
        assertThat(teamB.get(member.age.avg())).isEqualTo(35);


    }

    /**
     * 팀 A에 소속 된 모든 회원
     */
    @Test
    public void join(){
        List<Member> result = queryFactory
                .selectFrom(member)
                .join(member.team, team)
                .where(team.name.eq("teamA"))
                .fetch();

        assertThat(result).extracting("username")
                .containsExactly("member1", "member2");
    }

    @Test
    public void theta_join(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));

        List<Member> result = queryFactory.select(member)
                .from(member, team)
                .where(member.username.eq(team.name))
                .fetch();

        assertThat(result)
                .extracting("username") // 이게 컬럼명을
                .containsExactly("teamA","teamB"); // 이중에 있는지 검증?

    }


    /**
     *
     */
    @Test
    public void join_on_filtering() {
        List<Tuple> result = queryFactory
                .select(member, team)
                .from(member)
                .leftJoin(member.team, team).on(team.name.eq("teamA"))
                .fetch();

        for (Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }

    }

    @Test
    public void join_on_no_relation(){
        em.persist(new Member("teamA"));
        em.persist(new Member("teamB"));
        em.persist(new Member("teamC"));

        List<Tuple> result = queryFactory.select(member, team)
                .from(member)
                .leftJoin(team).on(member.username.eq(team.name))
                .fetch();

        for (Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }
    }

    @PersistenceUnit
    EntityManagerFactory emf;

    @Test
    public void fetchJoinNo(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isFalse();
    }

    @Test
    public void fetchJoin(){
        em.flush();
        em.clear();

        Member findMember = queryFactory
                .selectFrom(member)
                .join(member.team, team).fetchJoin()
                .where(member.username.eq("member1"))
                .fetchOne();

        boolean loaded = emf.getPersistenceUnitUtil().isLoaded(findMember.getTeam());
        assertThat(loaded).as("패치 조인 미적용").isTrue();
    }


    @Test
    public void subQuery(){

        QMember memberSub = new QMember("subMember");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.eq(
                        JPAExpressions
                                .select(memberSub.age.max())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(40);


    }

    @Test
    public void subQueryGoe(){

        QMember memberSub = new QMember("subMember");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.goe(
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20,30,40);


    }


    @Test
    public void subQueryIn(){

        QMember memberSub = new QMember("subMember");

        List<Member> result = queryFactory
                .selectFrom(member)
                .where(member.age.in(
                        JPAExpressions
                                .select(memberSub.age)
                                .from(memberSub)
                                .where(memberSub.age.gt(10))
                ))
                .fetch();

        assertThat(result).extracting("age")
                .containsExactly(20,30,40);


    }


    @Test
    public void selectSubQuery(){

        QMember memberSub = new QMember("subMember");

        List<Tuple> result = queryFactory
                .select(member.username,
                        JPAExpressions
                                .select(memberSub.age.avg())
                                .from(memberSub)
                )
                .from(member)
                .fetch();

        for (Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }
    }

    @Test
    public void basicCase(){
        List<String> result = queryFactory
                .select(member.age
                        .when(10).then("열살")
                        .when(20).then("스무살")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();

        for (String s : result){
            System.out.println("s = " + s);
        }
    }

    @Test
    public void complexCase(){
        List<String> result = queryFactory
                .select(new CaseBuilder()
                        .when(member.age.between(0, 20)).then("0~20")
                        .when(member.age.between(21, 30)).then("21~30")
                        .otherwise("기타")
                )
                .from(member)
                .fetch();

        for (String s : result){
            System.out.println("s = " + s);
        }


    }

    @Test
    public void constant() {
        List<Tuple> result = queryFactory
                .select(member.username, Expressions.constant("A"))
                .from(member)
                .fetch();

        for (Tuple tuple : result){
            System.out.println("tuple = " + tuple);
        }


    }

    @Test
    public void concat() {
        List<String> result = queryFactory
                .select(member.username.concat("_").concat(member.age.stringValue()))
                .from(member)
                .where(member.username.eq("member1"))
                .fetch();

        for (String s : result){
            System.out.println("s = " + s);
        }
    }


    @Test
    public void simpleProjection(){
        List<String> result = queryFactory
                .select(member.username)
                .from(member)
                .fetch();

        for (String s : result){
            System.out.println("s = " + s);
        }
    }


    @Test
    public void tupleProtection() {
        List<Tuple> result = queryFactory
                .select(member.username, member.age)
                .from(member)
                .fetch();

        for (Tuple tuple : result) {
            String username = tuple.get(member.username);
            Integer age = tuple.get(member.age);
            System.out.println("username = " + username);
            System.out.println("age = " + age);
        }
    }


    @Test
    public void findDTOByJPQL() {
        List<MemberDTO> result = em.createQuery("select new study.querydsl.dto.MemberDTO(m.username, m.age) from Member m", MemberDTO.class)
                .getResultList();

        for (MemberDTO memberDTO : result){
            System.out.println("memberDTO = " + memberDTO);
        }
    }


    @Test
    public void findDTOBySetter(){
        List<MemberDTO> result = queryFactory
                .select(
                        Projections.bean(MemberDTO.class,
                                member.username,
                                member.age)
                )
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : result){
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    @Test
    public void findDTOByField(){
        List<MemberDTO> result = queryFactory
                .select(
                        Projections.fields(MemberDTO.class,
                                member.username,
                                member.age)
                )
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : result){
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    @Test
    public void findDTOByConstructor(){
        List<UserDTO> result = queryFactory
                .select(
                        Projections.constructor(UserDTO.class, // DTO와 entity가 변수 타입이 일치해야함 , 클래스는 달라도 괜찮음
                                member.username,
                                member.age)// 다른 것들이 추가 되어도 컴파일 오류로 찾지 못하고 런타임 오류 발생
                )
                .from(member)
                .fetch();

        for (UserDTO memberDTO : result){
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    @Test
    public void findUserDTOByField(){

        QMember memberSub = new QMember("memberSub");

        List<UserDTO> result = queryFactory
                .select(
                        Projections.fields(UserDTO.class,
                                member.username.as("name"), // as를 사용해서 맞춰주면 됨
                                ExpressionUtils.as(
                                        JPAExpressions
                                                .select(memberSub.age.max())
                                                .from(memberSub), "age"
                                )
                        )
                )
                .from(member)
                .fetch();

        for (UserDTO memberDTO : result){
            System.out.println("memberDTO = " + memberDTO);
        }
    }


    @Test
    public void findDTOByQueryProjection(){
        List<MemberDTO> result = queryFactory
                .select(new QMemberDTO(member.username, member.age)) // 컴파일 오류를 찾을 수 있다
                .from(member)
                .fetch();

        for (MemberDTO memberDTO : result){
            System.out.println("memberDTO = " + memberDTO);
        }
    }

    @Test
    public void dynamicQuery_BooleanBuilder() {
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember1(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember1(String usernameCond, Integer ageCond) {

        BooleanBuilder builder = new BooleanBuilder();
//        BooleanBuilder builder = new BooleanBuilder(member.username.eq(usernameCond));// 필수 값은 바로 넣을 수도 있다
        if (usernameCond != null) {
            builder.and(member.username.eq(usernameCond));
        }

        if (ageCond != null) {
            builder.and(member.age.eq(ageCond));
        }



        return queryFactory
                .selectFrom(member)
                .where(builder)
                .fetch();
    }

    @Test
    public void dynamicQuery_WhereParam(){
        String usernameParam = "member1";
        Integer ageParam = 10;

        List<Member> result = searchMember2(usernameParam, ageParam);
        assertThat(result.size()).isEqualTo(1);
    }

    private List<Member> searchMember2(String usernameCond, Integer ageCond) {

        return queryFactory
                .selectFrom(member)
//                .where(usernameEq(usernameCond), ageEq(ageCond))
                .where(allEq(usernameCond,ageCond))
                .fetch();
    }

    private BooleanExpression allEq(String usernameCond, Integer ageCond) {
        return usernameEq(usernameCond).and(ageEq(ageCond));
    }

    private BooleanExpression ageEq(Integer ageCond) {
//        if (ageCond != null){
//            return member.age.eq(ageCond);
//        }
//        return null;
        return  ageCond != null ? member.age.eq(ageCond) : null;

    }

    private BooleanExpression usernameEq(String usernameCond) {
        if (usernameCond != null){
            return member.username.eq(usernameCond);
        } else {
            return null;
        }
    }

    @Test
    public void bulkUpdate() {
        long count = queryFactory
                .update(member)
                .set(member.username, "비회원")
                .where(member.age.lt(28))
                .execute();// 업데이트는 익스큐트로?

        em.flush(); // 업데이트같은 것은 바로 적용이 안됨으로 DB와 다를 수 있으니 초기화 후 다시 적용
        em.clear();

        List<Member> result = queryFactory
                .selectFrom(member)
                .from(member)
                .fetch();

        for (Member member1 : result){
            System.out.println("member = " + member1);
        }


    }

    @Test
    public void bulkAdd() {
        long count = queryFactory
                .update(member)
//                .set(member.age, member.age.add(1)) // 더하기
                .set(member.age, member.age.multiply(1)) // 곱하기
                .execute();
    }



    @Test
    public void bulkDelete() {
        long execute = queryFactory
                .delete(member)
                .where(member.age.gt(18))
                .execute();
    }


    @Test
    public void sqlFunction() {
        List<String> result = queryFactory
                .select(
                        Expressions.stringTemplate(
                                "function('replace', {0}, {1}, {2})", // 템플릿을 알아야함
                                member.username, "member", "M"))
                .from(member)
                .fetch();

        for (String s : result) {
            System.out.println("s = " + s);
        }
    }


    @Test
    public void sqlFunction2(){
        List<Member> result = queryFactory
                .select(member)
                .from(member)
                .where(member.username.eq(member.username.lower()))
//                .where(member.username.eq(
//                        Expressions.stringTemplate("function('lower', {0})", member.username)
//                ))
                .fetch();

        for (Member member1 : result) {
            System.out.println("member1 = " + member1);
        }
    }















}
