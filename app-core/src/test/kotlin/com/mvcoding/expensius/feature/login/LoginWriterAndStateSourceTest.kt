/*
 * Copyright (C) 2017 Mantas Varnagiris.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.mvcoding.expensius.feature.login

import com.mvcoding.expensius.datasource.DataSource
import com.mvcoding.expensius.datawriter.DataWriter
import com.mvcoding.expensius.feature.login.LoginPresenter.Login.AnonymousLogin
import com.mvcoding.expensius.feature.login.LoginPresenter.Login.ForcePreviousLoginAndLoseLocalDataIfUserAlreadyExists
import com.mvcoding.expensius.feature.login.LoginPresenter.Login.GetGoogleToken
import com.mvcoding.expensius.feature.login.LoginPresenter.Login.GoogleLogin
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState.FailedLogin
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState.Idle
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState.LoggingInAnonymously
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState.LoggingInWithGoogle
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState.SuccessfulLogin
import com.mvcoding.expensius.feature.login.LoginPresenter.LoginState.WaitingGoogleToken
import com.mvcoding.expensius.model.CreateTag
import com.mvcoding.expensius.model.GoogleToken
import com.mvcoding.expensius.model.Tag
import com.mvcoding.expensius.model.UserAlreadyLinkedException
import com.mvcoding.expensius.model.aCreateTag
import com.mvcoding.expensius.model.aGoogleToken
import com.mvcoding.expensius.model.aTag
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Before
import org.junit.Test
import rx.Observable
import rx.Observable.error
import rx.Observable.just
import rx.lang.kotlin.PublishSubject
import rx.observers.TestSubscriber

class LoginWriterAndStateSourceTest {
    val anonymousLoginSubject = PublishSubject<Unit>()
    val googleLoginSubject = PublishSubject<Unit>()

    val defaultTags = listOf(aCreateTag(), aCreateTag(), aCreateTag())

    val loginAnonymously = mock<() -> Observable<Unit>>()
    val loginWithGoogle = mock<(GoogleToken, Boolean) -> Observable<Unit>>()
    val tagsSource = mock<DataSource<List<Tag>>>()
    val defaultTagsSource = mock<DataSource<List<CreateTag>>>()
    val createTagsWriter = mock<DataWriter<List<CreateTag>>>()
    val loginWriterAndStateSource = LoginWriterAndStateSource(loginAnonymously, loginWithGoogle, tagsSource, defaultTagsSource, createTagsWriter)
    val subscriber = TestSubscriber<LoginState>()
    val anotherSubscriber = TestSubscriber<LoginState>()
    val anotherSubscriber2 = TestSubscriber<LoginState>()
    val anotherSubscriber3 = TestSubscriber<LoginState>()

    @Before
    fun setUp() {
        whenever(loginAnonymously()).thenReturn(anonymousLoginSubject)
        whenever(loginWithGoogle(any(), any())).thenReturn(googleLoginSubject)
        whenever(tagsSource.data()).thenReturn(just(emptyList()))
        whenever(defaultTagsSource.data()).thenReturn(just(defaultTags))
        loginWriterAndStateSource.data().subscribe(subscriber)
    }

    @Test
    fun `initially emits Idle state`() {
        subscriber.assertValue(Idle)
    }

    @Test
    fun `can login anonymously and writes default tags`() {
        loginWriterAndStateSource.write(AnonymousLogin)
        loginWriterAndStateSource.data().subscribe(anotherSubscriber)

        succeedAnonymousLogin()
        loginWriterAndStateSource.data().subscribe(anotherSubscriber2)

        subscriber.assertValues(Idle, LoggingInAnonymously, SuccessfulLogin)
        anotherSubscriber.assertValues(LoggingInAnonymously, SuccessfulLogin)
        anotherSubscriber2.assertValues(SuccessfulLogin)
        verify(loginAnonymously, times(1)).invoke()
        verify(createTagsWriter).write(defaultTags)
    }

    @Test
    fun `anonymous login can fail`() {
        val throwable = Throwable()
        loginWriterAndStateSource.write(AnonymousLogin)

        failAnonymousLogin(throwable)
        loginWriterAndStateSource.data().subscribe(anotherSubscriber)

        subscriber.assertValues(Idle, LoggingInAnonymously, FailedLogin(throwable))
        anotherSubscriber.assertValues(FailedLogin(throwable))
    }

    @Test
    fun `can login with google and writes default tags`() {
        val googleToken = aGoogleToken()
        loginWriterAndStateSource.write(GetGoogleToken)
        loginWriterAndStateSource.data().subscribe(anotherSubscriber)

        loginWriterAndStateSource.write(GoogleLogin(googleToken))
        loginWriterAndStateSource.data().subscribe(anotherSubscriber2)

        succeedGoogleLogin()
        loginWriterAndStateSource.data().subscribe(anotherSubscriber3)

        subscriber.assertValues(Idle, WaitingGoogleToken, LoggingInWithGoogle, SuccessfulLogin)
        anotherSubscriber.assertValues(WaitingGoogleToken, LoggingInWithGoogle, SuccessfulLogin)
        anotherSubscriber2.assertValues(LoggingInWithGoogle, SuccessfulLogin)
        anotherSubscriber3.assertValues(SuccessfulLogin)
        verify(loginWithGoogle, times(1)).invoke(googleToken, false)
        verify(createTagsWriter).write(defaultTags)
    }

    @Test
    fun `google login can fail`() {
        val throwable = Throwable()
        val googleToken = aGoogleToken()

        loginWriterAndStateSource.write(GoogleLogin(googleToken))

        failGoogleLogin(throwable)
        loginWriterAndStateSource.data().subscribe(anotherSubscriber)

        subscriber.assertValues(Idle, LoggingInWithGoogle, FailedLogin(throwable))
        anotherSubscriber.assertValues(FailedLogin(throwable))
    }

    @Test
    fun `can force google login if it failed because user is already linked`() {
        val throwable = UserAlreadyLinkedException(Throwable())
        val googleToken = aGoogleToken()

        loginWriterAndStateSource.write(ForcePreviousLoginAndLoseLocalDataIfUserAlreadyExists)
        loginWriterAndStateSource.write(GoogleLogin(googleToken))
        loginWriterAndStateSource.write(ForcePreviousLoginAndLoseLocalDataIfUserAlreadyExists)
        failGoogleLogin(throwable)
        whenever(loginWithGoogle(googleToken, true)).thenReturn(just(Unit))
        loginWriterAndStateSource.write(ForcePreviousLoginAndLoseLocalDataIfUserAlreadyExists)

        subscriber.assertValues(Idle, LoggingInWithGoogle, FailedLogin(throwable), LoggingInWithGoogle, SuccessfulLogin)
    }

    @Test
    fun `does not write default tags when tags already exist`() {
        whenever(tagsSource.data()).thenReturn(just(listOf(aTag(), aTag(), aTag())))

        loginWriterAndStateSource.write(AnonymousLogin)
        succeedAnonymousLogin()

        verify(createTagsWriter, never()).write(any())
    }

    @Test
    fun `ignores current tag checking errors`() {
        whenever(tagsSource.data()).thenReturn(error(Throwable()))

        loginWriterAndStateSource.write(AnonymousLogin)
        succeedAnonymousLogin()

        verify(createTagsWriter, never()).write(any())
        subscriber.assertValues(Idle, LoggingInAnonymously, SuccessfulLogin)
    }

    @Test
    fun `ignores tag creating errors`() {
        whenever(createTagsWriter.write(any())).thenThrow(RuntimeException())

        loginWriterAndStateSource.write(AnonymousLogin)
        succeedAnonymousLogin()

        subscriber.assertValues(Idle, LoggingInAnonymously, SuccessfulLogin)
    }

    fun succeedAnonymousLogin() = anonymousLoginSubject.onNext(Unit)
    fun failAnonymousLogin(throwable: Throwable) = anonymousLoginSubject.onError(throwable)
    fun succeedGoogleLogin() = googleLoginSubject.onNext(Unit)
    fun failGoogleLogin(throwable: Throwable) = googleLoginSubject.onError(throwable)
}