--
-- PostgreSQL database dump
--

\restrict rfgpnm53xMrAixpsRHfTgtgW1xHtSj2ZhCJDvdb7B3HBVxrNQrL6hnL7s4Aksa8

-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6

-- Started on 2026-11-20 21:56:54

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 2 (class 3079 OID 41376)
-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- TOC entry 5242 (class 0 OID 0)
-- Dependencies: 2
-- Name: EXTENSION pgcrypto; Type: COMMENT; Schema: -; Owner: 
--

COMMENT ON EXTENSION pgcrypto IS 'cryptographic functions';


--
-- TOC entry 297 (class 1255 OID 33043)
-- Name: fn_atualizar_status_midia(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.fn_atualizar_status_midia() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
DECLARE
    v_status VARCHAR(15);
    v_tipo   VARCHAR(10);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT m.status, m.tipo
          INTO v_status, v_tipo
          FROM public.midia m
         WHERE m.id_midia = NEW.id_midia
         FOR UPDATE;

        IF NOT FOUND THEN
            RAISE EXCEPTION 'Midia % nao encontrada', NEW.id_midia;
        END IF;

        IF v_status IS DISTINCT FROM 'DISPONIVEL' THEN
            RAISE EXCEPTION 'Midia % nao esta disponivel (status atual: %)',
                NEW.id_midia, v_status;
        END IF;

        IF v_tipo = 'FISICA' THEN
            IF NOT EXISTS (
                SELECT 1 FROM public.midia_fisica
                 WHERE id_midia = NEW.id_midia
            ) THEN
                RAISE EXCEPTION 'Midia fisica % nao possui cadastro especializado',
                    NEW.id_midia;
            END IF;

        ELSIF v_tipo = 'DIGITAL' THEN
            IF NOT EXISTS (
                SELECT 1 FROM public.midia_digital
                 WHERE id_midia = NEW.id_midia
            ) THEN
                RAISE EXCEPTION 'Midia digital % nao possui cadastro de licenca',
                    NEW.id_midia;
            END IF;

            IF EXISTS (
                SELECT 1
                  FROM public.midia_digital
                 WHERE id_midia = NEW.id_midia
                   AND validade IS NOT NULL
                   AND validade < CURRENT_DATE
            ) THEN
                RAISE EXCEPTION 'Licenca da midia % esta vencida', NEW.id_midia;
            END IF;
        ELSE
            RAISE EXCEPTION 'Tipo de midia invalido para a midia %: %',
                NEW.id_midia, v_tipo;
        END IF;

        UPDATE public.midia
           SET status = 'ALUGADA'
         WHERE id_midia = NEW.id_midia;

    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.data_devolucao IS NULL AND NEW.data_devolucao IS NOT NULL THEN
            UPDATE public.midia
               SET status = 'DISPONIVEL'
             WHERE id_midia = NEW.id_midia;
        END IF;
    END IF;

    RETURN NEW;
END;
$$;


ALTER FUNCTION public.fn_atualizar_status_midia() OWNER TO postgres;

--
-- TOC entry 299 (class 1255 OID 33045)
-- Name: fn_auditar_locacao(); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.fn_auditar_locacao() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
DECLARE
    v_func INTEGER := NULLIF(
        pg_catalog.current_setting('app.id_funcionario', TRUE), ''
    )::INTEGER;
BEGIN
    IF TG_OP = 'INSERT' THEN
        INSERT INTO public.auditoria_locacao
            (id_locacao, operacao, usuario_bd, id_funcionario_acao,
             dados_antigos, dados_novos)
        VALUES
            (NEW.id_locacao, 'INSERT', SESSION_USER, v_func,
             NULL, pg_catalog.to_jsonb(NEW));
        RETURN NEW;

    ELSIF TG_OP = 'UPDATE' THEN
        INSERT INTO public.auditoria_locacao
            (id_locacao, operacao, usuario_bd, id_funcionario_acao,
             dados_antigos, dados_novos)
        VALUES
            (OLD.id_locacao, 'UPDATE', SESSION_USER, v_func,
             pg_catalog.to_jsonb(OLD), pg_catalog.to_jsonb(NEW));
        RETURN NEW;

    ELSIF TG_OP = 'DELETE' THEN
        INSERT INTO public.auditoria_locacao
            (id_locacao, operacao, usuario_bd, id_funcionario_acao,
             dados_antigos, dados_novos)
        VALUES
            (OLD.id_locacao, 'DELETE', SESSION_USER, v_func,
             pg_catalog.to_jsonb(OLD), NULL);
        RETURN OLD;
    END IF;

    RETURN NULL;
END;
$$;


ALTER FUNCTION public.fn_auditar_locacao() OWNER TO postgres;

--
-- TOC entry 284 (class 1255 OID 41414)
-- Name: fn_autenticar_funcionario(character varying, text); Type: FUNCTION; Schema: public; Owner: postgres
--

CREATE FUNCTION public.fn_autenticar_funcionario(p_login character varying, p_senha text) RETURNS TABLE(id_funcionario integer, nome character varying, perfil character varying)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $_$
    SELECT f.id_funcionario, f.nome, f.perfil
      FROM public.funcionario f
     WHERE f.login = p_login
       AND f.ativo IS TRUE
       -- Ignora hashes antigos/de demonstracao que nao sejam bcrypt.
       AND CASE
             WHEN f.senha_hash LIKE '$2%' THEN
                 f.senha_hash = public.crypt(p_senha, f.senha_hash)
             ELSE FALSE
           END;
$_$;


ALTER FUNCTION public.fn_autenticar_funcionario(p_login character varying, p_senha text) OWNER TO postgres;

--
-- TOC entry 296 (class 1255 OID 33047)
-- Name: sp_realizar_locacao(integer, integer, integer[], integer, integer); Type: PROCEDURE; Schema: public; Owner: postgres
--

CREATE PROCEDURE public.sp_realizar_locacao(IN p_id_cliente integer, IN p_id_funcionario integer, IN p_midias integer[], IN p_dias integer, INOUT p_id_locacao integer)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
DECLARE
    v_ativo BOOLEAN;
    v_qtd   INTEGER;
    v_soma  NUMERIC(10,2);
BEGIN
    IF p_dias IS NULL OR p_dias <= 0 THEN
        RAISE EXCEPTION 'Quantidade de dias invalida: %', p_dias;
    END IF;

    IF p_midias IS NULL OR cardinality(p_midias) = 0 THEN
        RAISE EXCEPTION 'Informe ao menos uma midia';
    END IF;

    SELECT ativo INTO v_ativo FROM cliente WHERE id_cliente = p_id_cliente;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Cliente % nao encontrado', p_id_cliente;
    END IF;
    IF NOT v_ativo THEN
        RAISE EXCEPTION 'Cliente % esta inativo', p_id_cliente;
    END IF;

    SELECT ativo INTO v_ativo FROM funcionario WHERE id_funcionario = p_id_funcionario;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Funcionario % nao encontrado', p_id_funcionario;
    END IF;
    IF NOT v_ativo THEN
        RAISE EXCEPTION 'Funcionario % esta inativo', p_id_funcionario;
    END IF;

    SELECT COUNT(*), SUM(valor_diaria)
      INTO v_qtd, v_soma
      FROM midia
     WHERE id_midia = ANY(p_midias);

    IF v_qtd <> cardinality(p_midias) THEN
        RAISE EXCEPTION 'Lista de midias invalida (id inexistente ou repetido)';
    END IF;

    INSERT INTO locacao (id_cliente, id_funcionario, data_locacao,
                         prazo_devolucao, status, valor_total)
    VALUES (p_id_cliente, p_id_funcionario, CURRENT_DATE,
            CURRENT_DATE + p_dias, 'ATIVA', v_soma * p_dias)
    RETURNING id_locacao INTO p_id_locacao;

    INSERT INTO item_locacao (id_locacao, id_midia, valor_diaria)
    SELECT p_id_locacao, id_midia, valor_diaria
      FROM midia
     WHERE id_midia = ANY(p_midias);
END;
$$;


ALTER PROCEDURE public.sp_realizar_locacao(IN p_id_cliente integer, IN p_id_funcionario integer, IN p_midias integer[], IN p_dias integer, INOUT p_id_locacao integer) OWNER TO postgres;

--
-- TOC entry 298 (class 1255 OID 33048)
-- Name: sp_registrar_devolucao(integer, integer, boolean, numeric); Type: PROCEDURE; Schema: public; Owner: postgres
--

CREATE PROCEDURE public.sp_registrar_devolucao(IN p_id_locacao integer, IN p_id_midia integer, IN p_com_defeito boolean DEFAULT false, INOUT p_multa numeric DEFAULT 0)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'public'
    AS $$
DECLARE
    v_prazo        DATE;
    v_data_dev     DATE;
    v_valor_diaria NUMERIC(8,2);
    v_dias_atraso  INTEGER;
    v_abertos      INTEGER;
BEGIN
    -- Serializa as devolucoes pertencentes a mesma locacao.
    PERFORM 1
      FROM public.locacao
     WHERE id_locacao = p_id_locacao
     FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Locacao % nao encontrada', p_id_locacao;
    END IF;

    -- Mantem a protecao individual do item.
    SELECT l.prazo_devolucao,
           il.data_devolucao,
           il.valor_diaria
      INTO v_prazo, v_data_dev, v_valor_diaria
      FROM public.item_locacao il
      JOIN public.locacao l ON l.id_locacao = il.id_locacao
     WHERE il.id_locacao = p_id_locacao
       AND il.id_midia = p_id_midia
     FOR UPDATE OF il;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Item nao encontrado (locacao %, midia %)',
            p_id_locacao, p_id_midia;
    END IF;

    IF v_data_dev IS NOT NULL THEN
        RAISE EXCEPTION 'Midia % ja foi devolvida na locacao %',
            p_id_midia, p_id_locacao;
    END IF;

    v_dias_atraso := GREATEST(CURRENT_DATE - v_prazo, 0);
    p_multa := v_dias_atraso * v_valor_diaria;

    -- A trigger trg_item_locacao_status_midia atualiza o status da midia.
    UPDATE public.item_locacao
       SET data_devolucao = CURRENT_DATE,
           multa = p_multa
     WHERE id_locacao = p_id_locacao
       AND id_midia = p_id_midia;

    -- Se foi devolvida com defeito, encaminha para manutencao.
    IF p_com_defeito THEN
        UPDATE public.midia
           SET status = 'MANUTENCAO'
         WHERE id_midia = p_id_midia;
    END IF;

    SELECT COUNT(*)
      INTO v_abertos
      FROM public.item_locacao
     WHERE id_locacao = p_id_locacao
       AND data_devolucao IS NULL;

    IF v_abertos = 0 THEN
        UPDATE public.locacao
           SET status = 'CONCLUIDA',
               valor_total = valor_total + (
                   SELECT COALESCE(SUM(multa), 0)
                     FROM public.item_locacao
                    WHERE id_locacao = p_id_locacao
               )
         WHERE id_locacao = p_id_locacao
           AND status <> 'CONCLUIDA';
    END IF;
END;
$$;


ALTER PROCEDURE public.sp_registrar_devolucao(IN p_id_locacao integer, IN p_id_midia integer, IN p_com_defeito boolean, INOUT p_multa numeric) OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 240 (class 1259 OID 32985)
-- Name: auditoria_locacao; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.auditoria_locacao (
    id_auditoria integer NOT NULL,
    id_locacao integer NOT NULL,
    operacao character varying(10) NOT NULL,
    usuario_bd character varying(50) DEFAULT CURRENT_USER NOT NULL,
    data_hora timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    dados_antigos jsonb,
    dados_novos jsonb,
    id_funcionario_acao integer,
    CONSTRAINT auditoria_locacao_operacao_check CHECK (((operacao)::text = ANY ((ARRAY['INSERT'::character varying, 'UPDATE'::character varying, 'DELETE'::character varying])::text[])))
);


ALTER TABLE public.auditoria_locacao OWNER TO postgres;

--
-- TOC entry 239 (class 1259 OID 32984)
-- Name: auditoria_locacao_id_auditoria_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.auditoria_locacao_id_auditoria_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.auditoria_locacao_id_auditoria_seq OWNER TO postgres;

--
-- TOC entry 5247 (class 0 OID 0)
-- Dependencies: 239
-- Name: auditoria_locacao_id_auditoria_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.auditoria_locacao_id_auditoria_seq OWNED BY public.auditoria_locacao.id_auditoria;


--
-- TOC entry 225 (class 1259 OID 32791)
-- Name: cliente; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.cliente (
    id_cliente integer NOT NULL,
    nome character varying(100) NOT NULL,
    cpf character(11) NOT NULL,
    telefone character varying(20),
    email character varying(100),
    ativo boolean DEFAULT true NOT NULL,
    data_cadastro date DEFAULT CURRENT_DATE NOT NULL
);


ALTER TABLE public.cliente OWNER TO postgres;

--
-- TOC entry 224 (class 1259 OID 32790)
-- Name: cliente_id_cliente_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.cliente_id_cliente_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.cliente_id_cliente_seq OWNER TO postgres;

--
-- TOC entry 5249 (class 0 OID 0)
-- Dependencies: 224
-- Name: cliente_id_cliente_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.cliente_id_cliente_seq OWNED BY public.cliente.id_cliente;


--
-- TOC entry 227 (class 1259 OID 32809)
-- Name: funcionario; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.funcionario (
    id_funcionario integer NOT NULL,
    nome character varying(100) NOT NULL,
    cpf character(11) NOT NULL,
    telefone character varying(20),
    email character varying(100),
    login character varying(50) NOT NULL,
    senha_hash character varying(255) NOT NULL,
    perfil character varying(15) NOT NULL,
    ativo boolean DEFAULT true NOT NULL,
    CONSTRAINT funcionario_perfil_check CHECK (((perfil)::text = ANY ((ARRAY['ADMINISTRADOR'::character varying, 'FUNCIONARIO'::character varying])::text[])))
);


ALTER TABLE public.funcionario OWNER TO postgres;

--
-- TOC entry 226 (class 1259 OID 32808)
-- Name: funcionario_id_funcionario_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.funcionario_id_funcionario_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.funcionario_id_funcionario_seq OWNER TO postgres;

--
-- TOC entry 5252 (class 0 OID 0)
-- Dependencies: 226
-- Name: funcionario_id_funcionario_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.funcionario_id_funcionario_seq OWNED BY public.funcionario.id_funcionario;


--
-- TOC entry 223 (class 1259 OID 32780)
-- Name: genero; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.genero (
    id_genero integer NOT NULL,
    nome character varying(50) NOT NULL
);


ALTER TABLE public.genero OWNER TO postgres;

--
-- TOC entry 222 (class 1259 OID 32779)
-- Name: genero_id_genero_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.genero_id_genero_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.genero_id_genero_seq OWNER TO postgres;

--
-- TOC entry 5255 (class 0 OID 0)
-- Dependencies: 222
-- Name: genero_id_genero_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.genero_id_genero_seq OWNED BY public.genero.id_genero;


--
-- TOC entry 238 (class 1259 OID 32958)
-- Name: item_locacao; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.item_locacao (
    id_item_locacao integer NOT NULL,
    id_locacao integer NOT NULL,
    id_midia integer NOT NULL,
    valor_diaria numeric(8,2) NOT NULL,
    data_devolucao date,
    multa numeric(8,2) DEFAULT 0 NOT NULL,
    CONSTRAINT item_locacao_multa_check CHECK ((multa >= (0)::numeric)),
    CONSTRAINT item_locacao_valor_diaria_check CHECK ((valor_diaria > (0)::numeric))
);


ALTER TABLE public.item_locacao OWNER TO postgres;

--
-- TOC entry 237 (class 1259 OID 32957)
-- Name: item_locacao_id_item_locacao_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.item_locacao_id_item_locacao_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.item_locacao_id_item_locacao_seq OWNER TO postgres;

--
-- TOC entry 5258 (class 0 OID 0)
-- Dependencies: 237
-- Name: item_locacao_id_item_locacao_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.item_locacao_id_item_locacao_seq OWNED BY public.item_locacao.id_item_locacao;


--
-- TOC entry 229 (class 1259 OID 32833)
-- Name: jogo; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.jogo (
    id_jogo integer NOT NULL,
    titulo character varying(150) NOT NULL,
    ano_lancamento smallint NOT NULL,
    classificacao_indicativa character varying(10) DEFAULT 'LIVRE'::character varying NOT NULL,
    id_genero integer NOT NULL,
    CONSTRAINT jogo_ano_lancamento_check CHECK (((ano_lancamento >= 1970) AND (ano_lancamento <= 2100)))
);


ALTER TABLE public.jogo OWNER TO postgres;

--
-- TOC entry 228 (class 1259 OID 32832)
-- Name: jogo_id_jogo_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.jogo_id_jogo_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.jogo_id_jogo_seq OWNER TO postgres;

--
-- TOC entry 5261 (class 0 OID 0)
-- Dependencies: 228
-- Name: jogo_id_jogo_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.jogo_id_jogo_seq OWNED BY public.jogo.id_jogo;


--
-- TOC entry 230 (class 1259 OID 32851)
-- Name: jogo_plataforma; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.jogo_plataforma (
    id_jogo integer NOT NULL,
    id_plataforma integer NOT NULL
);


ALTER TABLE public.jogo_plataforma OWNER TO postgres;

--
-- TOC entry 236 (class 1259 OID 32928)
-- Name: locacao; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.locacao (
    id_locacao integer NOT NULL,
    id_cliente integer NOT NULL,
    id_funcionario integer NOT NULL,
    data_locacao date DEFAULT CURRENT_DATE NOT NULL,
    prazo_devolucao date NOT NULL,
    status character varying(15) DEFAULT 'ATIVA'::character varying NOT NULL,
    valor_total numeric(10,2) DEFAULT 0 NOT NULL,
    CONSTRAINT ck_locacao_prazo CHECK ((prazo_devolucao >= data_locacao)),
    CONSTRAINT locacao_status_check CHECK (((status)::text = ANY ((ARRAY['ATIVA'::character varying, 'ATRASADA'::character varying, 'CONCLUIDA'::character varying])::text[]))),
    CONSTRAINT locacao_valor_total_check CHECK ((valor_total >= (0)::numeric))
);


ALTER TABLE public.locacao OWNER TO postgres;

--
-- TOC entry 235 (class 1259 OID 32927)
-- Name: locacao_id_locacao_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.locacao_id_locacao_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.locacao_id_locacao_seq OWNER TO postgres;

--
-- TOC entry 5265 (class 0 OID 0)
-- Dependencies: 235
-- Name: locacao_id_locacao_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.locacao_id_locacao_seq OWNED BY public.locacao.id_locacao;


--
-- TOC entry 232 (class 1259 OID 32869)
-- Name: midia; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.midia (
    id_midia integer NOT NULL,
    id_jogo integer NOT NULL,
    id_plataforma integer NOT NULL,
    tipo character varying(10) NOT NULL,
    status character varying(15) DEFAULT 'DISPONIVEL'::character varying NOT NULL,
    valor_diaria numeric(8,2) NOT NULL,
    CONSTRAINT midia_status_check CHECK (((status)::text = ANY ((ARRAY['DISPONIVEL'::character varying, 'ALUGADA'::character varying, 'MANUTENCAO'::character varying])::text[]))),
    CONSTRAINT midia_tipo_check CHECK (((tipo)::text = ANY ((ARRAY['FISICA'::character varying, 'DIGITAL'::character varying])::text[]))),
    CONSTRAINT midia_valor_diaria_check CHECK ((valor_diaria > (0)::numeric))
);


ALTER TABLE public.midia OWNER TO postgres;

--
-- TOC entry 234 (class 1259 OID 32907)
-- Name: midia_digital; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.midia_digital (
    id_midia integer NOT NULL,
    codigo_licenca character varying(40) NOT NULL,
    validade date,
    tipo character varying(10) DEFAULT 'DIGITAL'::character varying NOT NULL,
    CONSTRAINT ck_md_tipo CHECK (((tipo)::text = 'DIGITAL'::text))
);


ALTER TABLE public.midia_digital OWNER TO postgres;

--
-- TOC entry 233 (class 1259 OID 32890)
-- Name: midia_fisica; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.midia_fisica (
    id_midia integer NOT NULL,
    codigo_barras character varying(30) NOT NULL,
    estado_conservacao character varying(10) DEFAULT 'BOM'::character varying NOT NULL,
    tipo character varying(10) DEFAULT 'FISICA'::character varying NOT NULL,
    CONSTRAINT ck_mf_tipo CHECK (((tipo)::text = 'FISICA'::text)),
    CONSTRAINT midia_fisica_estado_conservacao_check CHECK (((estado_conservacao)::text = ANY ((ARRAY['NOVO'::character varying, 'BOM'::character varying, 'REGULAR'::character varying, 'RUIM'::character varying])::text[])))
);


ALTER TABLE public.midia_fisica OWNER TO postgres;

--
-- TOC entry 231 (class 1259 OID 32868)
-- Name: midia_id_midia_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.midia_id_midia_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.midia_id_midia_seq OWNER TO postgres;

--
-- TOC entry 5270 (class 0 OID 0)
-- Dependencies: 231
-- Name: midia_id_midia_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.midia_id_midia_seq OWNED BY public.midia.id_midia;


--
-- TOC entry 221 (class 1259 OID 32769)
-- Name: plataforma; Type: TABLE; Schema: public; Owner: postgres
--

CREATE TABLE public.plataforma (
    id_plataforma integer NOT NULL,
    nome character varying(50) NOT NULL
);


ALTER TABLE public.plataforma OWNER TO postgres;

--
-- TOC entry 220 (class 1259 OID 32768)
-- Name: plataforma_id_plataforma_seq; Type: SEQUENCE; Schema: public; Owner: postgres
--

CREATE SEQUENCE public.plataforma_id_plataforma_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.plataforma_id_plataforma_seq OWNER TO postgres;

--
-- TOC entry 5273 (class 0 OID 0)
-- Dependencies: 220
-- Name: plataforma_id_plataforma_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: postgres
--

ALTER SEQUENCE public.plataforma_id_plataforma_seq OWNED BY public.plataforma.id_plataforma;


--
-- TOC entry 241 (class 1259 OID 33002)
-- Name: vw_catalogo_disponivel; Type: VIEW; Schema: public; Owner: postgres
--

CREATE VIEW public.vw_catalogo_disponivel AS
 SELECT m.id_midia,
    j.titulo,
    g.nome AS genero,
    p.nome AS plataforma,
    m.tipo,
    m.valor_diaria,
    COALESCE(mf.codigo_barras, md.codigo_licenca) AS identificador
   FROM (((((public.midia m
     JOIN public.jogo j ON ((j.id_jogo = m.id_jogo)))
     JOIN public.genero g ON ((g.id_genero = j.id_genero)))
     JOIN public.plataforma p ON ((p.id_plataforma = m.id_plataforma)))
     LEFT JOIN public.midia_fisica mf ON ((mf.id_midia = m.id_midia)))
     LEFT JOIN public.midia_digital md ON ((md.id_midia = m.id_midia)))
  WHERE (((m.status)::text = 'DISPONIVEL'::text) AND ((((m.tipo)::text = 'FISICA'::text) AND (mf.id_midia IS NOT NULL) AND (md.id_midia IS NULL)) OR (((m.tipo)::text = 'DIGITAL'::text) AND (md.id_midia IS NOT NULL) AND (mf.id_midia IS NULL) AND ((md.validade IS NULL) OR (md.validade >= CURRENT_DATE)))));


ALTER VIEW public.vw_catalogo_disponivel OWNER TO postgres;

--
-- TOC entry 245 (class 1259 OID 33022)
-- Name: vw_faturamento_mensal; Type: VIEW; Schema: public; Owner: postgres
--

CREATE VIEW public.vw_faturamento_mensal AS
 SELECT to_char((data_locacao)::timestamp with time zone, 'YYYY-MM'::text) AS mes,
    count(DISTINCT id_locacao) AS qtd_locacoes,
    sum(valor_total) AS faturamento
   FROM public.locacao l
  WHERE ((status)::text = 'CONCLUIDA'::text)
  GROUP BY (to_char((data_locacao)::timestamp with time zone, 'YYYY-MM'::text));


ALTER VIEW public.vw_faturamento_mensal OWNER TO postgres;

--
-- TOC entry 244 (class 1259 OID 33017)
-- Name: vw_historico_cliente; Type: VIEW; Schema: public; Owner: postgres
--

CREATE VIEW public.vw_historico_cliente AS
 SELECT c.id_cliente,
    c.nome AS cliente,
    l.id_locacao,
    j.titulo,
    p.nome AS plataforma,
    l.data_locacao,
    il.data_devolucao,
    il.valor_diaria,
    il.multa,
    l.status
   FROM (((((public.cliente c
     JOIN public.locacao l ON ((l.id_cliente = c.id_cliente)))
     JOIN public.item_locacao il ON ((il.id_locacao = l.id_locacao)))
     JOIN public.midia m ON ((m.id_midia = il.id_midia)))
     JOIN public.jogo j ON ((j.id_jogo = m.id_jogo)))
     JOIN public.plataforma p ON ((p.id_plataforma = m.id_plataforma)));


ALTER VIEW public.vw_historico_cliente OWNER TO postgres;

--
-- TOC entry 246 (class 1259 OID 33026)
-- Name: vw_jogos_mais_alugados; Type: VIEW; Schema: public; Owner: postgres
--

CREATE VIEW public.vw_jogos_mais_alugados AS
 SELECT j.titulo,
    g.nome AS genero,
    count(il.id_item_locacao) AS vezes_alugado
   FROM (((public.item_locacao il
     JOIN public.midia m ON ((m.id_midia = il.id_midia)))
     JOIN public.jogo j ON ((j.id_jogo = m.id_jogo)))
     JOIN public.genero g ON ((g.id_genero = j.id_genero)))
  GROUP BY j.titulo, g.nome;


ALTER VIEW public.vw_jogos_mais_alugados OWNER TO postgres;

--
-- TOC entry 242 (class 1259 OID 33007)
-- Name: vw_locacoes_ativas; Type: VIEW; Schema: public; Owner: postgres
--

CREATE VIEW public.vw_locacoes_ativas AS
 SELECT l.id_locacao,
    c.nome AS cliente,
    f.nome AS funcionario,
    j.titulo,
    p.nome AS plataforma,
    l.data_locacao,
    l.prazo_devolucao,
    l.status,
    il.id_item_locacao,
    il.id_midia,
    il.valor_diaria
   FROM ((((((public.locacao l
     JOIN public.cliente c ON ((c.id_cliente = l.id_cliente)))
     JOIN public.funcionario f ON ((f.id_funcionario = l.id_funcionario)))
     JOIN public.item_locacao il ON ((il.id_locacao = l.id_locacao)))
     JOIN public.midia m ON ((m.id_midia = il.id_midia)))
     JOIN public.jogo j ON ((j.id_jogo = m.id_jogo)))
     JOIN public.plataforma p ON ((p.id_plataforma = m.id_plataforma)))
  WHERE (il.data_devolucao IS NULL);


ALTER VIEW public.vw_locacoes_ativas OWNER TO postgres;

--
-- TOC entry 243 (class 1259 OID 33012)
-- Name: vw_locacoes_atrasadas; Type: VIEW; Schema: public; Owner: postgres
--

CREATE VIEW public.vw_locacoes_atrasadas AS
 SELECT l.id_locacao,
    c.nome AS cliente,
    c.telefone,
    j.titulo,
    l.prazo_devolucao,
    (CURRENT_DATE - l.prazo_devolucao) AS dias_atraso,
    (((CURRENT_DATE - l.prazo_devolucao))::numeric * il.valor_diaria) AS multa_estimada
   FROM ((((public.locacao l
     JOIN public.cliente c ON ((c.id_cliente = l.id_cliente)))
     JOIN public.item_locacao il ON ((il.id_locacao = l.id_locacao)))
     JOIN public.midia m ON ((m.id_midia = il.id_midia)))
     JOIN public.jogo j ON ((j.id_jogo = m.id_jogo)))
  WHERE ((il.data_devolucao IS NULL) AND (l.prazo_devolucao < CURRENT_DATE));


ALTER VIEW public.vw_locacoes_atrasadas OWNER TO postgres;

--
-- TOC entry 4995 (class 2604 OID 32988)
-- Name: auditoria_locacao id_auditoria; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.auditoria_locacao ALTER COLUMN id_auditoria SET DEFAULT nextval('public.auditoria_locacao_id_auditoria_seq'::regclass);


--
-- TOC entry 4977 (class 2604 OID 32794)
-- Name: cliente id_cliente; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cliente ALTER COLUMN id_cliente SET DEFAULT nextval('public.cliente_id_cliente_seq'::regclass);


--
-- TOC entry 4980 (class 2604 OID 32812)
-- Name: funcionario id_funcionario; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.funcionario ALTER COLUMN id_funcionario SET DEFAULT nextval('public.funcionario_id_funcionario_seq'::regclass);


--
-- TOC entry 4976 (class 2604 OID 32783)
-- Name: genero id_genero; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.genero ALTER COLUMN id_genero SET DEFAULT nextval('public.genero_id_genero_seq'::regclass);


--
-- TOC entry 4993 (class 2604 OID 32961)
-- Name: item_locacao id_item_locacao; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_locacao ALTER COLUMN id_item_locacao SET DEFAULT nextval('public.item_locacao_id_item_locacao_seq'::regclass);


--
-- TOC entry 4982 (class 2604 OID 32836)
-- Name: jogo id_jogo; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jogo ALTER COLUMN id_jogo SET DEFAULT nextval('public.jogo_id_jogo_seq'::regclass);


--
-- TOC entry 4989 (class 2604 OID 32931)
-- Name: locacao id_locacao; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.locacao ALTER COLUMN id_locacao SET DEFAULT nextval('public.locacao_id_locacao_seq'::regclass);


--
-- TOC entry 4984 (class 2604 OID 32872)
-- Name: midia id_midia; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia ALTER COLUMN id_midia SET DEFAULT nextval('public.midia_id_midia_seq'::regclass);


--
-- TOC entry 4975 (class 2604 OID 32772)
-- Name: plataforma id_plataforma; Type: DEFAULT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.plataforma ALTER COLUMN id_plataforma SET DEFAULT nextval('public.plataforma_id_plataforma_seq'::regclass);


--
-- TOC entry 5068 (class 2606 OID 33000)
-- Name: auditoria_locacao auditoria_locacao_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.auditoria_locacao
    ADD CONSTRAINT auditoria_locacao_pkey PRIMARY KEY (id_auditoria);


--
-- TOC entry 5021 (class 2606 OID 32805)
-- Name: cliente cliente_cpf_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_cpf_key UNIQUE (cpf);


--
-- TOC entry 5023 (class 2606 OID 32807)
-- Name: cliente cliente_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_email_key UNIQUE (email);


--
-- TOC entry 5025 (class 2606 OID 32803)
-- Name: cliente cliente_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.cliente
    ADD CONSTRAINT cliente_pkey PRIMARY KEY (id_cliente);


--
-- TOC entry 5027 (class 2606 OID 32827)
-- Name: funcionario funcionario_cpf_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.funcionario
    ADD CONSTRAINT funcionario_cpf_key UNIQUE (cpf);


--
-- TOC entry 5029 (class 2606 OID 32829)
-- Name: funcionario funcionario_email_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.funcionario
    ADD CONSTRAINT funcionario_email_key UNIQUE (email);


--
-- TOC entry 5031 (class 2606 OID 32831)
-- Name: funcionario funcionario_login_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.funcionario
    ADD CONSTRAINT funcionario_login_key UNIQUE (login);


--
-- TOC entry 5033 (class 2606 OID 32825)
-- Name: funcionario funcionario_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.funcionario
    ADD CONSTRAINT funcionario_pkey PRIMARY KEY (id_funcionario);


--
-- TOC entry 5017 (class 2606 OID 32789)
-- Name: genero genero_nome_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.genero
    ADD CONSTRAINT genero_nome_key UNIQUE (nome);


--
-- TOC entry 5019 (class 2606 OID 32787)
-- Name: genero genero_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.genero
    ADD CONSTRAINT genero_pkey PRIMARY KEY (id_genero);


--
-- TOC entry 5063 (class 2606 OID 32971)
-- Name: item_locacao item_locacao_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_locacao
    ADD CONSTRAINT item_locacao_pkey PRIMARY KEY (id_item_locacao);


--
-- TOC entry 5037 (class 2606 OID 32845)
-- Name: jogo jogo_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jogo
    ADD CONSTRAINT jogo_pkey PRIMARY KEY (id_jogo);


--
-- TOC entry 5059 (class 2606 OID 32946)
-- Name: locacao locacao_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT locacao_pkey PRIMARY KEY (id_locacao);


--
-- TOC entry 5052 (class 2606 OID 32915)
-- Name: midia_digital midia_digital_codigo_licenca_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia_digital
    ADD CONSTRAINT midia_digital_codigo_licenca_key UNIQUE (codigo_licenca);


--
-- TOC entry 5054 (class 2606 OID 32913)
-- Name: midia_digital midia_digital_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia_digital
    ADD CONSTRAINT midia_digital_pkey PRIMARY KEY (id_midia);


--
-- TOC entry 5048 (class 2606 OID 32901)
-- Name: midia_fisica midia_fisica_codigo_barras_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia_fisica
    ADD CONSTRAINT midia_fisica_codigo_barras_key UNIQUE (codigo_barras);


--
-- TOC entry 5050 (class 2606 OID 32899)
-- Name: midia_fisica midia_fisica_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia_fisica
    ADD CONSTRAINT midia_fisica_pkey PRIMARY KEY (id_midia);


--
-- TOC entry 5044 (class 2606 OID 32884)
-- Name: midia midia_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia
    ADD CONSTRAINT midia_pkey PRIMARY KEY (id_midia);


--
-- TOC entry 5040 (class 2606 OID 32857)
-- Name: jogo_plataforma pk_jogo_plataforma; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jogo_plataforma
    ADD CONSTRAINT pk_jogo_plataforma PRIMARY KEY (id_jogo, id_plataforma);


--
-- TOC entry 5013 (class 2606 OID 32778)
-- Name: plataforma plataforma_nome_key; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.plataforma
    ADD CONSTRAINT plataforma_nome_key UNIQUE (nome);


--
-- TOC entry 5015 (class 2606 OID 32776)
-- Name: plataforma plataforma_pkey; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.plataforma
    ADD CONSTRAINT plataforma_pkey PRIMARY KEY (id_plataforma);


--
-- TOC entry 5065 (class 2606 OID 32973)
-- Name: item_locacao uq_il_locacao_midia; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_locacao
    ADD CONSTRAINT uq_il_locacao_midia UNIQUE (id_locacao, id_midia);


--
-- TOC entry 5046 (class 2606 OID 41358)
-- Name: midia uq_midia_id_tipo; Type: CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia
    ADD CONSTRAINT uq_midia_id_tipo UNIQUE (id_midia, tipo);


--
-- TOC entry 5069 (class 1259 OID 33041)
-- Name: idx_auditoria_locacao; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_auditoria_locacao ON public.auditoria_locacao USING btree (id_locacao);


--
-- TOC entry 5060 (class 1259 OID 33036)
-- Name: idx_il_locacao; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_il_locacao ON public.item_locacao USING btree (id_locacao);


--
-- TOC entry 5061 (class 1259 OID 33037)
-- Name: idx_il_midia; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_il_midia ON public.item_locacao USING btree (id_midia);


--
-- TOC entry 5034 (class 1259 OID 33031)
-- Name: idx_jogo_genero; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_jogo_genero ON public.jogo USING btree (id_genero);


--
-- TOC entry 5035 (class 1259 OID 33040)
-- Name: idx_jogo_titulo; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_jogo_titulo ON public.jogo USING btree (titulo);


--
-- TOC entry 5038 (class 1259 OID 33032)
-- Name: idx_jp_plataforma; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_jp_plataforma ON public.jogo_plataforma USING btree (id_plataforma);


--
-- TOC entry 5055 (class 1259 OID 33034)
-- Name: idx_locacao_cliente; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_locacao_cliente ON public.locacao USING btree (id_cliente);


--
-- TOC entry 5056 (class 1259 OID 33035)
-- Name: idx_locacao_funcionario; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_locacao_funcionario ON public.locacao USING btree (id_funcionario);


--
-- TOC entry 5057 (class 1259 OID 33039)
-- Name: idx_locacao_status_prazo; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_locacao_status_prazo ON public.locacao USING btree (status, prazo_devolucao);


--
-- TOC entry 5041 (class 1259 OID 33033)
-- Name: idx_midia_jogo_plat; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_midia_jogo_plat ON public.midia USING btree (id_jogo, id_plataforma);


--
-- TOC entry 5042 (class 1259 OID 33038)
-- Name: idx_midia_status; Type: INDEX; Schema: public; Owner: postgres
--

CREATE INDEX idx_midia_status ON public.midia USING btree (status);


--
-- TOC entry 5066 (class 1259 OID 33042)
-- Name: uq_midia_locacao_aberta; Type: INDEX; Schema: public; Owner: postgres
--

CREATE UNIQUE INDEX uq_midia_locacao_aberta ON public.item_locacao USING btree (id_midia) WHERE (data_devolucao IS NULL);


--
-- TOC entry 5081 (class 2620 OID 41421)
-- Name: locacao trg_auditar_locacao; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_auditar_locacao AFTER INSERT OR DELETE OR UPDATE ON public.locacao FOR EACH ROW EXECUTE FUNCTION public.fn_auditar_locacao();


--
-- TOC entry 5082 (class 2620 OID 33044)
-- Name: item_locacao trg_item_locacao_status_midia; Type: TRIGGER; Schema: public; Owner: postgres
--

CREATE TRIGGER trg_item_locacao_status_midia AFTER INSERT OR UPDATE OF data_devolucao ON public.item_locacao FOR EACH ROW EXECUTE FUNCTION public.fn_atualizar_status_midia();


--
-- TOC entry 5080 (class 2606 OID 41416)
-- Name: auditoria_locacao fk_auditoria_funcionario; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.auditoria_locacao
    ADD CONSTRAINT fk_auditoria_funcionario FOREIGN KEY (id_funcionario_acao) REFERENCES public.funcionario(id_funcionario) ON DELETE SET NULL;


--
-- TOC entry 5078 (class 2606 OID 32974)
-- Name: item_locacao fk_il_locacao; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_locacao
    ADD CONSTRAINT fk_il_locacao FOREIGN KEY (id_locacao) REFERENCES public.locacao(id_locacao);


--
-- TOC entry 5079 (class 2606 OID 32979)
-- Name: item_locacao fk_il_midia; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.item_locacao
    ADD CONSTRAINT fk_il_midia FOREIGN KEY (id_midia) REFERENCES public.midia(id_midia);


--
-- TOC entry 5070 (class 2606 OID 32846)
-- Name: jogo fk_jogo_genero; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jogo
    ADD CONSTRAINT fk_jogo_genero FOREIGN KEY (id_genero) REFERENCES public.genero(id_genero);


--
-- TOC entry 5071 (class 2606 OID 32858)
-- Name: jogo_plataforma fk_jp_jogo; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jogo_plataforma
    ADD CONSTRAINT fk_jp_jogo FOREIGN KEY (id_jogo) REFERENCES public.jogo(id_jogo);


--
-- TOC entry 5072 (class 2606 OID 32863)
-- Name: jogo_plataforma fk_jp_plataforma; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.jogo_plataforma
    ADD CONSTRAINT fk_jp_plataforma FOREIGN KEY (id_plataforma) REFERENCES public.plataforma(id_plataforma);


--
-- TOC entry 5076 (class 2606 OID 32947)
-- Name: locacao fk_locacao_cliente; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT fk_locacao_cliente FOREIGN KEY (id_cliente) REFERENCES public.cliente(id_cliente);


--
-- TOC entry 5077 (class 2606 OID 32952)
-- Name: locacao fk_locacao_funcionario; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.locacao
    ADD CONSTRAINT fk_locacao_funcionario FOREIGN KEY (id_funcionario) REFERENCES public.funcionario(id_funcionario);


--
-- TOC entry 5075 (class 2606 OID 41370)
-- Name: midia_digital fk_md_midia_tipo; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia_digital
    ADD CONSTRAINT fk_md_midia_tipo FOREIGN KEY (id_midia, tipo) REFERENCES public.midia(id_midia, tipo) ON DELETE CASCADE;


--
-- TOC entry 5074 (class 2606 OID 41365)
-- Name: midia_fisica fk_mf_midia_tipo; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia_fisica
    ADD CONSTRAINT fk_mf_midia_tipo FOREIGN KEY (id_midia, tipo) REFERENCES public.midia(id_midia, tipo) ON DELETE CASCADE;


--
-- TOC entry 5073 (class 2606 OID 32885)
-- Name: midia fk_midia_jogo_plataforma; Type: FK CONSTRAINT; Schema: public; Owner: postgres
--

ALTER TABLE ONLY public.midia
    ADD CONSTRAINT fk_midia_jogo_plataforma FOREIGN KEY (id_jogo, id_plataforma) REFERENCES public.jogo_plataforma(id_jogo, id_plataforma);


--
-- TOC entry 5241 (class 0 OID 0)
-- Dependencies: 6
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: pg_database_owner
--

GRANT USAGE ON SCHEMA public TO grp_admin;
GRANT USAGE ON SCHEMA public TO grp_atendente;


--
-- TOC entry 5243 (class 0 OID 0)
-- Dependencies: 284
-- Name: FUNCTION fn_autenticar_funcionario(p_login character varying, p_senha text); Type: ACL; Schema: public; Owner: postgres
--

REVOKE ALL ON FUNCTION public.fn_autenticar_funcionario(p_login character varying, p_senha text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.fn_autenticar_funcionario(p_login character varying, p_senha text) TO grp_admin;
GRANT ALL ON FUNCTION public.fn_autenticar_funcionario(p_login character varying, p_senha text) TO grp_atendente;


--
-- TOC entry 5244 (class 0 OID 0)
-- Dependencies: 296
-- Name: PROCEDURE sp_realizar_locacao(IN p_id_cliente integer, IN p_id_funcionario integer, IN p_midias integer[], IN p_dias integer, INOUT p_id_locacao integer); Type: ACL; Schema: public; Owner: postgres
--

REVOKE ALL ON PROCEDURE public.sp_realizar_locacao(IN p_id_cliente integer, IN p_id_funcionario integer, IN p_midias integer[], IN p_dias integer, INOUT p_id_locacao integer) FROM PUBLIC;
GRANT ALL ON PROCEDURE public.sp_realizar_locacao(IN p_id_cliente integer, IN p_id_funcionario integer, IN p_midias integer[], IN p_dias integer, INOUT p_id_locacao integer) TO grp_admin;
GRANT ALL ON PROCEDURE public.sp_realizar_locacao(IN p_id_cliente integer, IN p_id_funcionario integer, IN p_midias integer[], IN p_dias integer, INOUT p_id_locacao integer) TO grp_atendente;


--
-- TOC entry 5245 (class 0 OID 0)
-- Dependencies: 298
-- Name: PROCEDURE sp_registrar_devolucao(IN p_id_locacao integer, IN p_id_midia integer, IN p_com_defeito boolean, INOUT p_multa numeric); Type: ACL; Schema: public; Owner: postgres
--

REVOKE ALL ON PROCEDURE public.sp_registrar_devolucao(IN p_id_locacao integer, IN p_id_midia integer, IN p_com_defeito boolean, INOUT p_multa numeric) FROM PUBLIC;
GRANT ALL ON PROCEDURE public.sp_registrar_devolucao(IN p_id_locacao integer, IN p_id_midia integer, IN p_com_defeito boolean, INOUT p_multa numeric) TO grp_admin;
GRANT ALL ON PROCEDURE public.sp_registrar_devolucao(IN p_id_locacao integer, IN p_id_midia integer, IN p_com_defeito boolean, INOUT p_multa numeric) TO grp_atendente;


--
-- TOC entry 5246 (class 0 OID 0)
-- Dependencies: 240
-- Name: TABLE auditoria_locacao; Type: ACL; Schema: public; Owner: postgres
--

GRANT SELECT ON TABLE public.auditoria_locacao TO grp_admin;


--
-- TOC entry 5248 (class 0 OID 0)
-- Dependencies: 225
-- Name: TABLE cliente; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.cliente TO grp_admin;
GRANT SELECT,INSERT,UPDATE ON TABLE public.cliente TO grp_atendente;


--
-- TOC entry 5250 (class 0 OID 0)
-- Dependencies: 224
-- Name: SEQUENCE cliente_id_cliente_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.cliente_id_cliente_seq TO grp_admin;
GRANT USAGE ON SEQUENCE public.cliente_id_cliente_seq TO grp_atendente;


--
-- TOC entry 5251 (class 0 OID 0)
-- Dependencies: 227
-- Name: TABLE funcionario; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.funcionario TO grp_admin;


--
-- TOC entry 5253 (class 0 OID 0)
-- Dependencies: 226
-- Name: SEQUENCE funcionario_id_funcionario_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.funcionario_id_funcionario_seq TO grp_admin;


--
-- TOC entry 5254 (class 0 OID 0)
-- Dependencies: 223
-- Name: TABLE genero; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.genero TO grp_admin;
GRANT SELECT ON TABLE public.genero TO grp_atendente;


--
-- TOC entry 5256 (class 0 OID 0)
-- Dependencies: 222
-- Name: SEQUENCE genero_id_genero_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.genero_id_genero_seq TO grp_admin;


--
-- TOC entry 5257 (class 0 OID 0)
-- Dependencies: 238
-- Name: TABLE item_locacao; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.item_locacao TO grp_admin;


--
-- TOC entry 5259 (class 0 OID 0)
-- Dependencies: 237
-- Name: SEQUENCE item_locacao_id_item_locacao_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.item_locacao_id_item_locacao_seq TO grp_admin;


--
-- TOC entry 5260 (class 0 OID 0)
-- Dependencies: 229
-- Name: TABLE jogo; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.jogo TO grp_admin;
GRANT SELECT,INSERT,UPDATE ON TABLE public.jogo TO grp_atendente;


--
-- TOC entry 5262 (class 0 OID 0)
-- Dependencies: 228
-- Name: SEQUENCE jogo_id_jogo_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.jogo_id_jogo_seq TO grp_admin;
GRANT USAGE ON SEQUENCE public.jogo_id_jogo_seq TO grp_atendente;


--
-- TOC entry 5263 (class 0 OID 0)
-- Dependencies: 230
-- Name: TABLE jogo_plataforma; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.jogo_plataforma TO grp_admin;
GRANT SELECT,INSERT ON TABLE public.jogo_plataforma TO grp_atendente;


--
-- TOC entry 5264 (class 0 OID 0)
-- Dependencies: 236
-- Name: TABLE locacao; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.locacao TO grp_admin;


--
-- TOC entry 5266 (class 0 OID 0)
-- Dependencies: 235
-- Name: SEQUENCE locacao_id_locacao_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.locacao_id_locacao_seq TO grp_admin;


--
-- TOC entry 5267 (class 0 OID 0)
-- Dependencies: 232
-- Name: TABLE midia; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.midia TO grp_admin;
GRANT SELECT ON TABLE public.midia TO grp_atendente;


--
-- TOC entry 5268 (class 0 OID 0)
-- Dependencies: 234
-- Name: TABLE midia_digital; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.midia_digital TO grp_admin;
GRANT SELECT ON TABLE public.midia_digital TO grp_atendente;


--
-- TOC entry 5269 (class 0 OID 0)
-- Dependencies: 233
-- Name: TABLE midia_fisica; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.midia_fisica TO grp_admin;
GRANT SELECT ON TABLE public.midia_fisica TO grp_atendente;


--
-- TOC entry 5271 (class 0 OID 0)
-- Dependencies: 231
-- Name: SEQUENCE midia_id_midia_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.midia_id_midia_seq TO grp_admin;


--
-- TOC entry 5272 (class 0 OID 0)
-- Dependencies: 221
-- Name: TABLE plataforma; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.plataforma TO grp_admin;
GRANT SELECT ON TABLE public.plataforma TO grp_atendente;


--
-- TOC entry 5274 (class 0 OID 0)
-- Dependencies: 220
-- Name: SEQUENCE plataforma_id_plataforma_seq; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON SEQUENCE public.plataforma_id_plataforma_seq TO grp_admin;


--
-- TOC entry 5275 (class 0 OID 0)
-- Dependencies: 241
-- Name: TABLE vw_catalogo_disponivel; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.vw_catalogo_disponivel TO grp_admin;
GRANT SELECT ON TABLE public.vw_catalogo_disponivel TO grp_atendente;


--
-- TOC entry 5276 (class 0 OID 0)
-- Dependencies: 245
-- Name: TABLE vw_faturamento_mensal; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.vw_faturamento_mensal TO grp_admin;
GRANT SELECT ON TABLE public.vw_faturamento_mensal TO grp_atendente;


--
-- TOC entry 5277 (class 0 OID 0)
-- Dependencies: 244
-- Name: TABLE vw_historico_cliente; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.vw_historico_cliente TO grp_admin;
GRANT SELECT ON TABLE public.vw_historico_cliente TO grp_atendente;


--
-- TOC entry 5278 (class 0 OID 0)
-- Dependencies: 246
-- Name: TABLE vw_jogos_mais_alugados; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.vw_jogos_mais_alugados TO grp_admin;
GRANT SELECT ON TABLE public.vw_jogos_mais_alugados TO grp_atendente;


--
-- TOC entry 5279 (class 0 OID 0)
-- Dependencies: 242
-- Name: TABLE vw_locacoes_ativas; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.vw_locacoes_ativas TO grp_admin;
GRANT SELECT ON TABLE public.vw_locacoes_ativas TO grp_atendente;


--
-- TOC entry 5280 (class 0 OID 0)
-- Dependencies: 243
-- Name: TABLE vw_locacoes_atrasadas; Type: ACL; Schema: public; Owner: postgres
--

GRANT ALL ON TABLE public.vw_locacoes_atrasadas TO grp_admin;
GRANT SELECT ON TABLE public.vw_locacoes_atrasadas TO grp_atendente;


-- Completed on 2026-11-20 21:56:54

--
-- PostgreSQL database dump complete
--

\unrestrict rfgpnm53xMrAixpsRHfTgtgW1xHtSj2ZhCJDvdb7B3HBVxrNQrL6hnL7s4Aksa8

