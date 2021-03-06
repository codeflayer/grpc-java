/*
 * Copyright 2020 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.xds.internal.sds;

import static com.google.common.base.Preconditions.checkNotNull;

import io.envoyproxy.envoy.api.v2.auth.CertificateValidationContext;
import io.envoyproxy.envoy.api.v2.auth.CommonTlsContext;
import io.envoyproxy.envoy.api.v2.auth.CommonTlsContext.CombinedCertificateValidationContext;
import io.envoyproxy.envoy.api.v2.auth.SdsSecretConfig;
import io.envoyproxy.envoy.api.v2.core.Node;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.xds.EnvoyServerProtoData.UpstreamTlsContext;
import io.grpc.xds.internal.sds.trust.SdsTrustManagerFactory;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.IOException;
import java.security.cert.CertStoreException;
import java.security.cert.CertificateException;
import java.util.concurrent.Executor;

/** A client SslContext provider that uses SDS to fetch secrets. */
final class SdsClientSslContextProvider extends SdsSslContextProvider {

  private SdsClientSslContextProvider(
      Node node,
      SdsSecretConfig certSdsConfig,
      SdsSecretConfig validationContextSdsConfig,
      CertificateValidationContext staticCertValidationContext,
      Executor watcherExecutor,
      Executor channelExecutor,
      UpstreamTlsContext upstreamTlsContext) {
    super(node,
        certSdsConfig,
        validationContextSdsConfig,
        staticCertValidationContext,
        watcherExecutor,
        channelExecutor, upstreamTlsContext);
  }

  static SdsClientSslContextProvider getProvider(
      UpstreamTlsContext upstreamTlsContext,
      Node node,
      Executor watcherExecutor,
      Executor channelExecutor) {
    checkNotNull(upstreamTlsContext, "upstreamTlsContext");
    CommonTlsContext commonTlsContext = upstreamTlsContext.getCommonTlsContext();
    SdsSecretConfig validationContextSdsConfig = null;
    CertificateValidationContext staticCertValidationContext = null;
    if (commonTlsContext.hasCombinedValidationContext()) {
      CombinedCertificateValidationContext combinedValidationContext =
          commonTlsContext.getCombinedValidationContext();
      if (combinedValidationContext.hasValidationContextSdsSecretConfig()) {
        validationContextSdsConfig =
            combinedValidationContext.getValidationContextSdsSecretConfig();
      }
      if (combinedValidationContext.hasDefaultValidationContext()) {
        staticCertValidationContext = combinedValidationContext.getDefaultValidationContext();
      }
    } else if (commonTlsContext.hasValidationContextSdsSecretConfig()) {
      validationContextSdsConfig = commonTlsContext.getValidationContextSdsSecretConfig();
    } else if (commonTlsContext.hasValidationContext()) {
      staticCertValidationContext = commonTlsContext.getValidationContext();
    }
    SdsSecretConfig certSdsConfig = null;
    if (commonTlsContext.getTlsCertificateSdsSecretConfigsCount() > 0) {
      certSdsConfig = commonTlsContext.getTlsCertificateSdsSecretConfigs(0);
    }
    return new SdsClientSslContextProvider(
        node,
        certSdsConfig,
        validationContextSdsConfig,
        staticCertValidationContext,
        watcherExecutor,
        channelExecutor,
        upstreamTlsContext);
  }

  @Override
  SslContextBuilder getSslContextBuilder(
      CertificateValidationContext localCertValidationContext)
      throws CertificateException, IOException, CertStoreException {
    SslContextBuilder sslContextBuilder =
        GrpcSslContexts.forClient()
            .trustManager(new SdsTrustManagerFactory(localCertValidationContext));
    if (tlsCertificate != null) {
      sslContextBuilder.keyManager(
          tlsCertificate.getCertificateChain().getInlineBytes().newInput(),
          tlsCertificate.getPrivateKey().getInlineBytes().newInput(),
          tlsCertificate.hasPassword() ? tlsCertificate.getPassword().getInlineString() : null);
    }
    return sslContextBuilder;
  }
}
