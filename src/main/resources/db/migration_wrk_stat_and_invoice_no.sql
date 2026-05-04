-- 신규 컬럼 추가 및 기존 데이터 백필
-- 실행 시점: 앱 구동 전 1회 수동 실행 (또는 Hibernate ddl-auto=update 가 처리)
-- 멱등성: IF NOT EXISTS 를 지원하지 않는 MySQL 버전이 있어 INFORMATION_SCHEMA 로 체크하는 방식 대신
--         재실행 시 컬럼 중복 에러가 나면 해당 ALTER 는 스킵해도 됨.

-- 1) top_sellers_out_ord.wrk_stat (주문수집=20, 송장발급=30, 송장전송완료=31)
ALTER TABLE top_sellers_out_ord
  ADD COLUMN wrk_stat INT NOT NULL DEFAULT 20;

-- 기존 데이터 백필: invoice_no 가 있으면 송장발급(30)
UPDATE top_sellers_out_ord
SET wrk_stat = 30
WHERE invoice_no IS NOT NULL
  AND wrk_stat = 20;

-- 2) imweb_order_section.invoice_no (아임웹에 등록된 송장번호 저장)
ALTER TABLE imweb_order_section
  ADD COLUMN invoice_no VARCHAR(100) NULL;
