-- =========================================================================
-- 모든 테이블을 utf8mb4 / utf8mb4_unicode_ci 로 통일
-- 실행법 (로컬):
--   mysql -u root -p dev_justenter < src/main/resources/db/convert_to_utf8mb4_unicode_ci.sql
-- 운영(liv_justenter) 에도 동일하게 실행.
-- =========================================================================

-- 1) DB 기본값 변경 (이후 Hibernate auto-ddl 로 새로 만들어지는 테이블이 이 설정을 상속)
ALTER DATABASE `liv_justenter` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
-- 운영: ALTER DATABASE `liv_justenter` CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 2) 기존 테이블 일괄 변환 (CONVERT TO ... 가 모든 문자열 컬럼도 같이 변환)
ALTER TABLE `top_sellers_out_ord` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `imweb_order_section` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `brand`               CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
ALTER TABLE `external_token`      CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 3) 검증: 모든 테이블이 utf8mb4_unicode_ci 인지 확인
-- SELECT TABLE_NAME, TABLE_COLLATION
-- FROM information_schema.TABLES
-- WHERE TABLE_SCHEMA = DATABASE();

-- 4) 컬럼 단위로도 확인 (혹시 누락된 컬럼이 있는지)
-- SELECT TABLE_NAME, COLUMN_NAME, COLLATION_NAME
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND COLLATION_NAME IS NOT NULL
--   AND COLLATION_NAME <> 'utf8mb4_unicode_ci';
