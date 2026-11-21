SELECT
    usuario,
    'public' AS schema,
    has_schema_privilege(
        usuario, 'public', 'USAGE'
    ) AS acesso_schema,

    has_function_privilege(
        usuario,
        'public.sp_realizar_locacao(integer, integer, integer[], integer, integer)',
        'EXECUTE'
    ) AS executar_locacao,

    has_function_privilege(
        usuario,
        'public.sp_registrar_devolucao(integer, integer, boolean, numeric)',
        'EXECUTE'
    ) AS executar_devolucao

FROM (
    VALUES
        ('usr_admin_teste'),
        ('usr_atendente_teste')
) AS usuarios(usuario);
