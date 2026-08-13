package com.revio.social.data.repository

import com.revio.social.core.network.ApiResult
import com.revio.social.core.network.isNetworkError
import com.revio.social.data.remote.api.AdminCarFamilyApi
import com.revio.social.data.remote.dto.admin.car_family.CarFamilyAdminDto
import com.revio.social.data.remote.dto.car_model.CarModelOption
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class AdminCarFamilyRepositoryImplTest {

    private val api: AdminCarFamilyApi = mockk()
    private lateinit var repository: AdminCarFamilyRepositoryImpl

    private val familyId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val carFamilyDto = CarFamilyAdminDto(
        id = familyId,
        brand = "Volkswagen",
        name = "Golf",
    )

    @Before
    fun setUp() {
        repository = AdminCarFamilyRepositoryImpl(api)
    }

    @Test
    fun `listFamilies success maps domain correctly`() = runTest {
        coEvery { api.listFamilies() } returns Response.success(listOf(carFamilyDto))

        val result = repository.listFamilies()

        assertTrue(result is ApiResult.Success)
        val data = (result as ApiResult.Success).data
        assertEquals(1, data.size)
        assertEquals(familyId, data[0].id)
        assertEquals("Volkswagen", data[0].brand)
        assertEquals("Golf", data[0].name)
    }

    @Test
    fun `listFamilies empty list maps to empty domain list`() = runTest {
        coEvery { api.listFamilies() } returns Response.success(emptyList())

        val result = repository.listFamilies()

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.isEmpty())
    }

    @Test
    fun `listFamilies 403 returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Admin access required"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.listFamilies() } returns Response.error(403, errorBody)

        val result = repository.listFamilies()

        assertTrue(result is ApiResult.Error)
        assertEquals("Admin access required", (result as ApiResult.Error).message)
    }

    @Test
    fun `listFamilies network error returns ApiResult Error with network code`() = runTest {
        coEvery { api.listFamilies() } throws IOException("Connection refused")

        val result = repository.listFamilies()

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }

    @Test
    fun `listModelsForFamily success returns the model options unchanged`() = runTest {
        val models = listOf(
            CarModelOption(id = UUID.fromString("00000000-0000-0000-0000-000000000003"), model = "Golf"),
            CarModelOption(id = UUID.fromString("00000000-0000-0000-0000-000000000004"), model = "Golf Plus"),
        )
        coEvery { api.listModelsForFamily(familyId) } returns Response.success(models)

        val result = repository.listModelsForFamily(familyId)

        assertTrue(result is ApiResult.Success)
        assertEquals(models, (result as ApiResult.Success).data)
    }

    @Test
    fun `listModelsForFamily empty list for a family with no models`() = runTest {
        coEvery { api.listModelsForFamily(familyId) } returns Response.success(emptyList())

        val result = repository.listModelsForFamily(familyId)

        assertTrue(result is ApiResult.Success)
        assertTrue((result as ApiResult.Success).data.isEmpty())
    }

    @Test
    fun `listModelsForFamily 404 returns ApiResult Error`() = runTest {
        val errorBody = """{"error":"Car family not found"}""".toResponseBody("application/json".toMediaType())
        coEvery { api.listModelsForFamily(familyId) } returns Response.error(404, errorBody)

        val result = repository.listModelsForFamily(familyId)

        assertTrue(result is ApiResult.Error)
        assertEquals("Car family not found", (result as ApiResult.Error).message)
    }

    @Test
    fun `listModelsForFamily network error returns ApiResult Error with network code`() = runTest {
        coEvery { api.listModelsForFamily(familyId) } throws IOException("Connection refused")

        val result = repository.listModelsForFamily(familyId)

        assertTrue(result is ApiResult.Error)
        assertTrue((result as ApiResult.Error).isNetworkError)
    }
}
