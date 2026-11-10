package cl.hogarya.infrastructure.persistence.handler;

import com.pgvector.PGvector;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * TypeHandler de MyBatis para vectores de pgvector (vector(768)).
 * Mapea arreglos float[] de Java al tipo nativo vector de PostgreSQL mediante com.pgvector.PGvector.
 */
@MappedTypes(float[].class)
@MappedJdbcTypes(value = {JdbcType.OTHER}, includeNullJdbcType = true)
public class PgVectorTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, float[] parameter, JdbcType jdbcType) throws SQLException {
        ps.setObject(i, new PGvector(parameter));
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object val = rs.getObject(columnName);
        return toFloatArray(val);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object val = rs.getObject(columnIndex);
        return toFloatArray(val);
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Object val = cs.getObject(columnIndex);
        return toFloatArray(val);
    }

    private float[] toFloatArray(Object val) throws SQLException {
        if (val == null) {
            return null;
        }
        if (val instanceof PGvector pgv) {
            return pgv.toArray();
        }
        if (val instanceof PGobject pgo) {
            return new PGvector(pgo.getValue()).toArray();
        }
        if (val instanceof String str) {
            return new PGvector(str).toArray();
        }
        return null;
    }
}
